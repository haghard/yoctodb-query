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

import java.nio.file.Paths
import scala.jdk.CollectionConverters.asScalaSetConverter
import scala.reflect.ClassTag

/** https://scalameta.org/docs/trees/guide.html
  * https://github.com/eed3si9n/ifdef/blob/main/plugin/src/main/scala/IfDefPlugin.scala
  */
object IndexDslGeneratorPlugin extends AutoPlugin {

  override def requires: sbt.Plugins = JvmPlugin
  override def trigger = allRequirements

  object autoImport {
    val genIndexDsl = taskKey[Seq[File]]("Generate Yoctodb index query dsl")
  }

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    autoImport.genIndexDsl := {
      val managedSourceDir = (Compile / sourceManaged).value
      writeFiles(genSource(managedSourceDir).toList, streams.value.log)
    }
  )

  val knownFilters =
    Set("games_yy", "games_dd", "games_mm", "games_stage", "games_ht", "games_at", "games_winner")

  val knownSorters =
    Set("games_yy", "games_dd", "games_mm", "games_ts")

  def genSource(
      sourceManagedPath: java.io.File
    ): Option[(scala.meta.Source, java.io.File)] =
    loadIndex()
      .map {
        case (sorted, filtered) =>
          if (
              sorted.asScala.intersect(knownSorters) == knownSorters &&
              (filtered.asScala.intersect(knownFilters) == knownFilters)
          )
            Some((generateSrc(), sourceManagedPath / "query" / "dsl" / "SearchIndexQueryDsl.scala"))
          else {
            println("Schema error !!!")
            None
          }
      }
      .getOrElse(None)

  def generateSrc(): scala.meta.Source = {
    val queryDslClazz =
      Defn.Class(
        mods = List(Mod.Final(), Mod.Case()),
        name = Type.Name("SearchIndexQueryDsl"),
        tparamClause = Type.ParamClause(List.empty),
        ctor = Ctor.Primary(
          mods = Nil,
          name = Name.Anonymous(),
          paramClauses = List(
            Term.ParamClause(
              List(
                mkFilterableParam[String]("games_ht", "homeTeam"),
                mkFilterableParam[String]("games_at", "awayTeam"),
                mkFilterableParam[String]("games_stage", "stage"),
                mkFilterableParam[String]("games_winner", "winner"),
                // mkSortableParam[Long]("games_ts", "gameTs"),
                mkBothParam[Long]("games_ts", "gameTs"),
                mkBothParam[Int]("games_yy", "yy"),
                mkBothParam[Int]("games_mm", "mm"),
                mkBothParam[Int]("games_dd", "dd"),
              )
            )
          ),
        ),
        templ = Template(
          origin = scala.meta.trees.Origin.None,
          early = Nil,
          inits = Nil,
          self = Self(
            name = Name.Anonymous(),
            decltpe = None,
          ),
          stats = Nil,
        ),
      )

    val imp1 =
      Import(importers =
        List(
          Importer(
            Term.Select(
              Term.Select(
                Term.Select(Term.Name("com"), name = Term.Name("yandex")),
                name = Term.Name("yoctodb"),
              ),
              name = Term.Name("query"),
            ),
            List(Importee.Wildcard()),
          )
        )
      )

    val imp2 =
      Import(importers =
        List(
          Importer(
            Term.Select(
              Term.Select(
                Term.Select(Term.Name("com"), name = Term.Name("yandex")),
                name = Term.Name("yoctodb"),
              ),
              name = Term.Name("util"),
            ),
            List(Importee.Name(Name("UnsignedByteArrays"))),
          )
        )
      )

    Source(
      stats = List(
        Pkg(
          ref = Term.Select(qual = Term.Name("query"), name = Term.Name("dsl")),
          body = Pkg.Body(List(imp1, imp2, queryDslClazz)),
        )
      )
    )

    /*source"""
      package query.dsl
      import com.yandex.yoctodb.query._
      import com.yandex.yoctodb.util.UnsignedByteArrays
      final class SearchIndexQueryDsl {
        $homeTeam
        $awayTeam
        $gameStage
        $gameWinner
        $gameTs
        $gameYyF
        $gameMm
        $gameDd
      }
    """*/
  }

  def loadIndex(): Either[Throwable, (java.util.Set[String], java.util.Set[String])] =
    Try {
      val indexPath = "indexes/games"
      val indexFile = Paths.get(indexPath).toFile()
      if (indexFile.exists() && indexFile.isFile()) {
        val reader = DatabaseFormat.getCurrent().getDatabaseReader()
        val db: V1Database = reader.from(Buffer.mmap(indexFile, false)).asInstanceOf[V1Database]
        println(
          s"★ ★ ★ Index [${indexFile.length() / (1024 * 1024)}MB / NumOfDocs: ${db.getDocumentCount()} ] ★ ★ ★\n"
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

  def mkFilterableParam[T: ClassTag](
      indexFieldName: String,
      caseClassFieldName: String,
    ): Term.Param = {
    val termType = Type.Name("Filterable")
    val termTypeParamType = inferTypeParam[T]

    val rightHs =
      q"""
          new ${scala.meta.Init(termType, termType, Seq.empty)}[${termTypeParamType}] {
            val fieldName = $indexFieldName

            def =:=(v: ${termTypeParamType}): TermCondition =
              QueryBuilder.eq(fieldName, UnsignedByteArrays.from(v))

            def in(vs: scala.collection.immutable.Set[${termTypeParamType}]): TermCondition =
              QueryBuilder.in(fieldName, vs.toSeq.map(UnsignedByteArrays.from(_)):_*)

            def >(v: ${termTypeParamType}): TermCondition =
             QueryBuilder.gt(fieldName, UnsignedByteArrays.from(v))

            def >=(v: ${termTypeParamType}): TermCondition =
              QueryBuilder.gte(fieldName, UnsignedByteArrays.from(v))

            def <(v: ${termTypeParamType}): TermCondition =
              QueryBuilder.lt(fieldName, UnsignedByteArrays.from(v))

            def <=(v: ${termTypeParamType}): TermCondition =
              QueryBuilder.lte(fieldName, UnsignedByteArrays.from(v))
          }
      """

    Term.Param(
      mods = List(Mod.ValParam()),
      name = Term.Name(caseClassFieldName),
      decltpe = Some(Type.Apply(termType, Type.ArgClause(List(termTypeParamType)))),
      default = Some(rightHs),
    )
  }

  def mkBothParam[T: ClassTag](
      indexFieldName: String,
      caseClassFieldName: String,
    ): Term.Param = {
    val termType = Type.Name("Both")
    val termTypeParamType = inferTypeParam[T]
    val rightHs =
      q"""
          new ${scala.meta.Init(termType, termType, Seq.empty)}[${termTypeParamType}] {
            val fieldName = $indexFieldName

            def =:=(v: ${termTypeParamType}): TermCondition =
              QueryBuilder.eq(fieldName, UnsignedByteArrays.from(v))

            def in(vs: scala.collection.immutable.Set[${termTypeParamType}]): TermCondition =
              QueryBuilder.in(fieldName, vs.toSeq.map(UnsignedByteArrays.from(_)):_*)

            def >(v: ${termTypeParamType}): TermCondition =
             QueryBuilder.gt(fieldName, UnsignedByteArrays.from(v))

            def >=(v: ${termTypeParamType}): TermCondition =
              QueryBuilder.gte(fieldName, UnsignedByteArrays.from(v))

            def <(v: ${termTypeParamType}): TermCondition =
              QueryBuilder.lt(fieldName, UnsignedByteArrays.from(v))

            def <=(v: ${termTypeParamType}): TermCondition =
              QueryBuilder.lte(fieldName, UnsignedByteArrays.from(v))

            def desc(): com.yandex.yoctodb.query.Order = QueryBuilder.desc(fieldName)
            def asc(): com.yandex.yoctodb.query.Order = QueryBuilder.asc(fieldName)
          }
      """

    Term.Param(
      mods = List(Mod.ValParam()),
      name = Term.Name(caseClassFieldName),
      decltpe = Some(Type.Apply(termType, Type.ArgClause(List(termTypeParamType)))),
      default = Some(rightHs),
    )

  }

  def mkSortableParam[T: ClassTag](
      indexFieldName: String,
      caseClassFieldName: String,
    ): Term.Param = {
    val termType = Type.Name("Sortable")
    val termTypeParamType = inferTypeParam[T]

    val rightHs =
      q"""
          new ${scala.meta.Init(termType, termType, Seq.empty)}[${termTypeParamType}] {
            val fieldName = $indexFieldName
            def desc(): com.yandex.yoctodb.query.Order = QueryBuilder.desc(fieldName)
            def asc(): com.yandex.yoctodb.query.Order = QueryBuilder.asc(fieldName)
          }
      """

    Term.Param(
      mods = List(Mod.ValParam()),
      name = Term.Name(caseClassFieldName),
      decltpe = Some(Type.Apply(termType, Type.ArgClause(List(termTypeParamType)))),
      default = Some(rightHs),
    )
  }

  private def inferTypeParam[T: ClassTag]: Type.Name = {
    val Str_ = classOf[String]
    val Int_ = classOf[Int]
    val Long_ = classOf[Long]
    val Double_ = classOf[Double]

    implicitly[ClassTag[T]].runtimeClass match {
      case Str_ => Type.Name(Str_.getSimpleName)
      case Int_ => Type.Name("Int")
      case Long_ => Type.Name("Long")
      case Double_ => Type.Name("Double")
      case other =>
        throw new IllegalArgumentException(s"Unsupported type: $other")
    }
  }

  def writeFiles(
      outputs: List[(scala.meta.Source, java.io.File)],
      log: ManagedLogger,
    ): List[java.io.File] = {
    import java.nio.file.Files
    import java.nio.file.Path
    import java.io.FileOutputStream

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
}
