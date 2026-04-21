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
import java.util
import scala.annotation.implicitNotFound
import scala.jdk.CollectionConverters.*

@implicitNotFound("Primitive[${T}] isn't supported")
sealed abstract class PrimitiveType[T](val typeName: Type.Name, val isNumetic: Boolean)

object PrimitiveType {
  implicit object Int_ extends PrimitiveType[Int](Type.Name("Int"), true)
  implicit object Long_ extends PrimitiveType[Long](Type.Name("Long"), true)
  implicit object String_ extends PrimitiveType[String](Type.Name("String"), false)
  implicit object Double_ extends PrimitiveType[Double](Type.Name("Double"), true)

  private val terms = Map(
    Int_.typeName.value -> Some(Int_),
    Long_.typeName.value -> Some(Long_),
    String_.typeName.value -> Some(String_),
    Double_.typeName.value -> Some(Double_),
  )

  def fromConfig(columnType: String, isFilterable: Boolean): Option[(PrimitiveType[?], Type.Name)] =
    terms.getOrElse(columnType, None).map { tp =>
      val ops =
        if (isFilterable)
          if (tp.isNumetic) t"FilterableNum" else t"Filterable"
        else
          t"Sortable"

      (tp, ops)
    }

  def fromConfigBoth(columnType: String): Option[(PrimitiveType[?], Type.Name)] =
    terms.getOrElse(columnType, None).map { tp =>
      val ops = if(tp.isNumetic) t"BothNum" else t"Both"
      (tp, ops)
    }
}

object IndexGeneratorPlugin extends AutoPlugin {
  val configFilePath = "/src/main/resources/application.conf"

  override def requires: JvmPlugin.type = sbt.plugins.JvmPlugin

  override def trigger: sbt.PluginTrigger = allRequirements

  object autoImport {
    val genIndexDsl = taskKey[Seq[File]]("Generates query dsl")
  }

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    autoImport.genIndexDsl := {
      val managedSourceDir = (Compile / sourceManaged).value
      val cfgFilePath = baseDirectory.value.toString + configFilePath
      println(s"★ ★ ★ Load config file $cfgFilePath ★ ★ ★ ★")

      writeFiles(
        genSources(ConfigFactory.parseFile(new File(cfgFilePath)).getConfig("player-stat"), "playerStat", managedSourceDir),
        streams.value.log,
      )

      writeFiles(
        genSources(ConfigFactory.parseFile(new File(cfgFilePath)).getConfig("game-info"), "gameInfo", managedSourceDir),
        streams.value.log,
      )

      writeFiles(
        genSources(ConfigFactory.parseFile(new File(cfgFilePath)).getConfig("game-stat"), "gameStat", managedSourceDir),
        streams.value.log
      )
    }
  )

  def genSources(
    gamesConfig: Config,
    indexFileName: String,
    sourceManagedPath: java.io.File,
  ): List[(scala.meta.Source, java.io.File)] =
    loadIndex("indexes/" + indexFileName).map { case (filtered, sorted) =>
      val f = filtered.asScala
      val s = sorted.asScala

      val filteredAndSorted = f.intersect(s)
      filteredAndSorted.foreach(f.remove(_))
      filteredAndSorted.foreach(s.remove(_))

      /*println(f.mkString(","))
      println(s.mkString(","))
      println(filteredAndSorted.mkString(","))*/

      val filters =
        f.map { columnName =>
          (
            columnName,
            columnName.charAt(0).toTitleCase + columnName.substring(1),
            PrimitiveType
              .fromConfig(gamesConfig.getString(s"filters.$columnName"), isFilterable = true)
              .getOrElse(throw new Exception(s"Filter($columnName) definition error")),
          )
        }

      val sorters =
        s.map { columnName =>
          (
            columnName,
            columnName.charAt(0).toTitleCase + columnName.substring(1),
            PrimitiveType
              .fromConfig(gamesConfig.getString(s"sorters.$columnName"), isFilterable = false)
              .getOrElse(throw new Exception(s"Filter($columnName) definition error")),
          )
        }

      val both =
        filteredAndSorted.map { columnName =>
          (
            columnName,
            columnName.charAt(0).toTitleCase + columnName.substring(1),
            PrimitiveType
              .fromConfigBoth(gamesConfig.getString(s"filters.$columnName"))
              .getOrElse(throw new Exception(s"Both($columnName) definition error")),
          )
        }

      val schema = (filters ++ sorters ++ both).toList

      val generatedTerms =
        schema.map {
          case (_, name, (tp, opsTrait)) =>
            (
              generateTermClassContent(name, tp.typeName.value, opsTrait),
              sourceManagedPath / "query" / "dsl" / indexFileName / s"${name}.scala",
            )
        }

      val nameWithCtor = schema.map {
        case (configKey, name, _) =>
          (
            configKey.charAt(0).toLower + configKey.substring(1),
            q"${scala.meta.Term.Name(name)}()",
          )
      }

      generatedTerms :+ (
        genIndex(nameWithCtor, indexFileName),
        sourceManagedPath / "query" / "dsl" / s"${indexFileName}.scala",
      )
    }.getOrElse(List.empty)

  def loadIndex(indexPath: String): Either[Throwable, (java.util.Set[String], java.util.Set[String])] =
    Try {
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

        (new util.HashSet[String](filters.keySet()), new util.HashSet[String](sorters.keySet()))
      }
      else throw new Exception(s"Couldn't find or open file $indexPath")
    }.toEither

  def writeFiles(
      outputs: List[(scala.meta.Source, java.io.File)],
      log: ManagedLogger,
    ): List[java.io.File] = {
    log.info(s"★ ★ ★  Generate ${outputs.size} files ★ ★ ★")
    val genFiles =
      outputs.map {
        case (src, dest) =>
          log.info(s"★ ★ ★ Directory ${dest.getParent}")
          Files.createDirectories(Path.of(dest.getParent))
          Using.resource(new FileOutputStream(dest))(_.write(src.syntax.getBytes()))
          dest
      }

    genFiles.map(_.getAbsolutePath()).foreach(log.info(_))
    genFiles
  }

  def generateTermClassContent(
      termName: String,
      scalaTypeStr: String,
      columnTypeName: scala.meta.Type.Name,
    ): scala.meta.Source = {

    val term = Type.Name(termName)
    val scalaType = Type.Name(scalaTypeStr)
    val column = termName.charAt(0).toLower + termName.substring(1)

    val nameVal: scala.meta.Term.Param =
      param"override val name: String = $column"

    val classDef: Defn.Class = {
      columnTypeName.value match {
        case "Filterable" =>
          q"""final case class ${term} (${nameVal}) extends IndexColumn[$scalaType] {${Helpers.filterable(columnTypeName, scalaType)}}"""

        case "FilterableNum" =>
          q"""final case class ${term}(${nameVal}) extends IndexColumn[$scalaType] with DoubleWriter {${Helpers.filterableNum(columnTypeName, scalaType, scalaTypeStr == PrimitiveType.Double_.typeName.value)}}"""

        case "Sortable" =>
          q"""final case class ${term}(${nameVal}) extends IndexColumn[$scalaType] {${Helpers.sortable(columnTypeName, scalaType)}}"""

        case "Both" =>
          q"""final case class ${term} (${nameVal}) extends IndexColumn[$scalaType] {${Helpers.both(columnTypeName, scalaType)}}"""

        case "BothNum" =>
          q"""final case class ${term} (${nameVal}) extends IndexColumn[$scalaType] with DoubleWriter {${Helpers.bothNum(columnTypeName, scalaType, scalaTypeStr == PrimitiveType.Double_.typeName.value)}}"""

        case unknown =>
          throw new Exception(s"Unexpected $unknown")
      }
    }

    val termSource =
      s"""package query.dsl
       |import com.yandex.yoctodb.query._
       |import com.yandex.yoctodb.util.UnsignedByteArrays
       |${classDef.syntax}
      """
        .stripMargin
        .parse[Source]
        .fold(e => throw e.details, identity)

    println(s"★ ★ ★ ${termName} AST")
    println(termSource.structure)
    println("★ ★ ★ ★ ★ ★ ★ ★ ★")
    termSource
  }

  def genIndex(columns: List[(String, Term.Apply)], indexFileName: String): scala.meta.Source = {

    val vals =
      columns.map {
        case (termName, termCtor) =>
          Defn.Val(
            mods = Nil,
            pats = List(Pat.Var(name = Term.Name(termName))),
            decltpe = None,
            rhs = termCtor,
          )

        /*Defn.Def(
          mods = Nil,
          name = Term.Name(termName.charAt(0).toLower + termName.substring(1)),
          paramClauseGroups = Nil,
          decltpe = None,
          body = opTerm
        )*/
      }

    // Defn.Object(???)

    val searchIndexObject =
      Defn.Object(
        mods = Nil,
        name = Term.Name(s"${indexFileName}"),
        templ = scala
          .meta
          .Template(
            earlyClause = None,
            inits = Nil,
            body = Template.Body(selfOpt = None, stats = vals),
            derives = Nil,
          ),
      )

    Source(
      stats = List(
        Pkg(
          ref = Term.Select(qual = Term.Name("query"), name = Term.Name("dsl")),
          body = Pkg.Body.apply(stats = List(searchIndexObject)),
        )
      )
    )
  }
}
