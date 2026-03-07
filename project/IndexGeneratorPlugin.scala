import com.typesafe.config.*
import com.yandex.yoctodb.DatabaseFormat
import sbt.AutoPlugin
import sbt.plugins.JvmPlugin
import sbt.*
import sbt.Keys.*

import scala.meta.*
import scala.util.*
import com.yandex.yoctodb.util.buf.Buffer
import com.yandex.yoctodb.v1.immutable.V1Database
import com.yandex.yoctodb.immutable.*
import sbt.internal.util.ManagedLogger

import java.nio.file.Files
import java.nio.file.Path
import java.io.FileOutputStream

import java.nio.file.Paths
import scala.annotation.implicitNotFound
import scala.jdk.CollectionConverters.*

@implicitNotFound("Primitive[${T}] isn't supported")
sealed abstract class PrimitiveType[T](val typeName: Type.Name, val isNumetic: Boolean)

object PrimitiveType {
  implicit object Int_ extends PrimitiveType[Int](Type.Name("Int"), true)
  implicit object Long_ extends PrimitiveType[Long](Type.Name("Long"), true)
  implicit object String_ extends PrimitiveType[String](Type.Name("String"), false)

  private val terms = Map(
    Int_.typeName.value -> Some(Int_),
    Long_.typeName.value -> Some(Long_),
    String_.typeName.value -> Some(String_),
  )

  def fromStr(columnType: String): Option[PrimitiveType[?]] =
    terms.getOrElse(columnType, None)
}

object IndexGeneratorPlugin extends AutoPlugin {

  override def requires: JvmPlugin.type = sbt.plugins.JvmPlugin

  override def trigger: sbt.PluginTrigger = allRequirements

  object autoImport {
    val genIndexDsl = taskKey[Seq[File]]("Generates query dsl")
  }

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    autoImport.genIndexDsl := {
      val managedSourceDir = (Compile / sourceManaged).value
      writeFiles(genSources(ConfigFactory.parseFile(new File("./src/main/resources/application.conf")), managedSourceDir), streams.value.log)
    }
  )

  def genSources(
      config: Config,
      sourceManagedPath: java.io.File,
    ): List[(scala.meta.Source, java.io.File)] =
    loadIndex()
      .map {
        case (sorted, filtered) =>

          val filters =
            config.getObject("filters").keySet().asScala.map { key =>
              val cfg = config.getConfig(s"filters.$key")
              val columnName = cfg.getString("column_name")
              val columnType = cfg.getString("type")

              val pType =
                PrimitiveType
                  .fromStr(columnType)
                  .getOrElse(throw new Exception(s"Filter($columnName) definition error"))

              (
                key,
                columnName.charAt(0).toTitleCase + columnName.substring(1),
                pType,
                if(pType.isNumetic) t"FilterableNum" else t"Filterable"
              )
            }

          val sorters =
            config.getObject("sorters").keySet().asScala.map { key =>
              val cfg = config.getConfig(s"sorters.$key")
              val columnName = cfg.getString("column_name")
              val columnType = cfg.getString("type")
              (
                key,
                columnName.charAt(0).toTitleCase + columnName.substring(1),
                PrimitiveType
                  .fromStr(columnType)
                  .getOrElse(throw new Exception(s"Sorter($columnName) definition error")),
                t"Sortable"
              )
            }

          val schema = (filters ++ sorters).toList

          val generatedTerms =
            schema.map { case (_, name, tp, opsTrait) =>
              (generateTerm(name, tp.typeName.value, opsTrait), sourceManagedPath / "query" / "dsl" / s"${name}.scala")
            }

          val in = schema.map { case (k, name, _, _) => (k, q"${scala.meta.Term.Name(name)}()") }
          generatedTerms :+ (genIndex(in), sourceManagedPath / "query" / "dsl" / "SearchIndex.scala")
      }
      .getOrElse(List.empty)

  def loadIndex(): Either[Throwable, (java.util.Set[String], java.util.Set[String])] =
    Try {
      val indexPath = "indexes/games"
      val indexFile = Paths.get(indexPath).toFile()
      if (indexFile.exists() && indexFile.isFile()) {
        val reader = DatabaseFormat.getCurrent().getDatabaseReader()
        val db: V1Database = reader.from(Buffer.mmap(indexFile, false)).asInstanceOf[V1Database]
        println(
          s"★ ★ ★ Loaded index from:${indexFile} [${indexFile.length() / (1024 * 1024)}MB / NumOfDocs: ${db.getDocumentCount()} ] ★ ★ ★\n"
        )

        val sortersField = db.getClass().getDeclaredField("sorters")
        sortersField.setAccessible(true)
        val sorters = sortersField.get(db).asInstanceOf[java.util.Map[String, SortableIndex]]
        println("★ ★ ★  Sorters ★ ★ ★")
        sorters.keySet().forEach { (skey: String) =>
          println(skey + " -> " + sorters.get(skey))
        }
        println("★ ★ ★ ★ ★ ★ ★ ★ ★ ★ ★ ★\n")

        val filtersField = db.getClass().getDeclaredField("filters")
        filtersField.setAccessible(true)
        val filters = filtersField.get(db).asInstanceOf[java.util.Map[String, FilterableIndex]]
        println("★ ★ ★  Filters  ★ ★ ★")
        filters.keySet().forEach { (fkey: String) =>
          println(fkey + " -> " + filters.get(fkey))
        }
        println("★ ★ ★ ★ ★ ★ ★ ★ ★ ★ ★ ★\n")

        val storersField = db.getClass().getDeclaredField("storers")
        storersField.setAccessible(true)
        val storers = storersField.get(db).asInstanceOf[java.util.Map[String, StoredIndex]]
        println("★ ★ ★  Storers  ★ ★ ★")
        storers.keySet().forEach { (skey: String) =>
          println(skey + " -> " + storers.get(skey))
        }
        println("★ ★ ★ ★ ★ ★ ★ ★ ★ ★ ★ ★\n")

        (sorters.keySet(), filters.keySet())

      }
      else throw new Exception(s"Couldn't find or open file $indexPath")
    }.toEither

  def writeFiles(
      outputs: List[(scala.meta.Source, java.io.File)],
      log: ManagedLogger,
    ): List[java.io.File] = {

    log.info("★ ★ ★  Generated files ★ ★ ★")
    val genFiles =
      outputs.map {
        case (src, dest) =>
          Files.createDirectories(Path.of(dest.getParent()))
          Using.resource(new FileOutputStream(dest))(_.write(src.syntax.getBytes()))
          dest
      }

    genFiles.map(_.getAbsolutePath).foreach(log.info(_))
    log.info("★ ★ ★ ★ ★ ★")
    genFiles
  }

  def generateTerm(
    termName: String,
    scalaTypeStr: String,
    columnTypeName: scala.meta.Type.Name,
  ): scala.meta.Source = {
    val term = Type.Name(termName)
    val clmType = Type.Name(scalaTypeStr)

    val columnName = termName.charAt(0).toLower + termName.substring(1)
    val ctorParam = param"override val name: String = $columnName"

    columnTypeName.value match {
      case "Filterable" =>
        source"""
        package query.dsl
        import com.yandex.yoctodb.query._
        import com.yandex.yoctodb.util.UnsignedByteArrays
        final case class ${term}(${ctorParam}) extends IndexColumn[$clmType] {
          ${CompanionFunctions.buildFilterableTerm(columnTypeName, clmType)}
        }
      """

      case "FilterableNum" =>
        source"""
        package query.dsl
        import com.yandex.yoctodb.query._
        import com.yandex.yoctodb.util.UnsignedByteArrays
        final case class ${term}(${ctorParam}) extends IndexColumn[$clmType] {
          ${CompanionFunctions.buildFilterableNumTerm(columnTypeName, clmType)}
        }
      """

      case "Sortable" =>
        source"""
        package query.dsl
        import com.yandex.yoctodb.query._
        final case class ${term}(${ctorParam}) extends IndexColumn[$clmType] {
          ${CompanionFunctions.buildSortableTerm(columnTypeName, clmType)}
        }
      """
    }
  }

  def genIndex(columns: List[(String, Term.Apply)]): scala.meta.Source = {

    val vals =
      columns.map { case (termName, opTerm) =>
        Defn.Val(
          mods = Nil,
          pats = List(Pat.Var(name = Term.Name(termName.charAt(0).toLower + termName.substring(1)))),
          decltpe = None,
          rhs = opTerm
        )

        /*Defn.Def(
          mods = Nil,
          name = Term.Name(termName.charAt(0).toLower + termName.substring(1)),
          paramClauseGroups = Nil,
          decltpe = None,
          body = opTerm
        )*/
      }


    val indexObject =
      Defn.Object(
        mods = Nil,
        name = Term.Name("SearchIndex"),
        templ = scala.meta.Template(
          earlyClause = None,
          inits = Nil,
          body = Template.Body(selfOpt = None, stats = vals),
          derives = Nil,
        )
      )

    Source(
      stats = List(
        Pkg(
          ref = Term.Select(Term.Name("query"), Term.Name("dsl")),
          body = Pkg.Body(List(indexObject))
        )
      )
    )
  }
}
