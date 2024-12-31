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

object IndexGeneratorPlugin extends AutoPlugin {

  override def requires = JvmPlugin
  override def trigger = allRequirements

  object autoImport {
    val genFiles = taskKey[Seq[File]]("Generate Yoctodb index schema files")
  }

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    autoImport.genFiles := {
      val managedSourceDir = (Compile / sourceManaged).value
      writeFiles(genSource(managedSourceDir).toList, streams.value.log)
    }
  )

  val expectedFiltered =
    Set("games_yy", "games_dd", "games_mm", "games_stage", "games_ht", "games_at", "games_winner")

  def genSource(
      sourceManagedPath: java.io.File
    ): Option[(scala.meta.Source, java.io.File)] =
    loadIndex()
      .map {
        case (sorted, filtered) =>
          if (
              sorted.contains("games_ts") && (filtered
                .asScala
                .intersect(expectedFiltered) == expectedFiltered)
          )
            Some((genIndex(), sourceManagedPath / "query" / "dsl" / "SearchIndexQueryDsl.scala"))
          else {
            println("Schema error !!!")
            None
          }
      }
      .getOrElse(None)

  def genIndex(): scala.meta.Source = {

    val homeTeam =
      Defn.Val(
        mods = Nil,
        pats = List(Pat.Var(name = Term.Name("homeTeam"))),
        decltpe = None,
        rhs = mkFilterable[String]("games_ht"),
      )

    val awayTeam =
      Defn.Val(
        Nil,
        pats = List(Pat.Var(name = Term.Name("awayTeam"))),
        decltpe = None,
        rhs = mkFilterable[String]("games_at"),
      )

    val gameStage =
      Defn.Val(
        Nil,
        pats = List(Pat.Var(name = Term.Name("gamesStage"))),
        decltpe = None,
        rhs = mkFilterable[String]("games_stage"),
      )

    val gameWinner =
      Defn.Val(
        Nil,
        pats = List(Pat.Var(name = Term.Name("gamesWinner"))),
        decltpe = None,
        rhs = mkFilterable[String]("games_winner"),
      )

    val gameTs =
      Defn.Val(
        Nil,
        pats = List(Pat.Var(name = Term.Name("gamesTs"))),
        decltpe = None,
        rhs = mkSortable[Long]("games_ts"),
      )

    val gameYyF =
      Defn.Val(
        Nil,
        pats = List(Pat.Var(name = Term.Name("gamesYy"))),
        decltpe = None,
        rhs = mkBoth[Int]("games_yy"),
      )

    val gameDd =
      Defn.Val(
        Nil,
        pats = List(Pat.Var(name = Term.Name("gamesDd"))),
        decltpe = None,
        rhs = mkBoth[Int]("games_dd"),
      )

    val gameMm =
      Defn.Val(
        Nil,
        pats = List(Pat.Var(name = Term.Name("gamesMm"))),
        decltpe = None,
        rhs = mkBoth[Int]("games_mm"),
      )

    source"""
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
    """
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

  def mkFilterable[T: ClassTag](fName: String): Term.Apply = {
    val termType = Type.Name("Filterable")
    val termTypeParamType: Type.Name = inferTypeParam[T]
    q"""
          new ${scala.meta.Init(termType, termType, Seq.empty)}[${termTypeParamType}] {
            val fieldName = $fName

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

  }

  def mkBoth[T: ClassTag](fName: String): Term.Apply = {
    val termType = Type.Name("Both")
    val termTypeParamType: Type.Name = inferTypeParam[T]
    q"""
          new ${scala.meta.Init(termType, termType, Seq.empty)}[${termTypeParamType}] {
            val fieldName = $fName

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

  }

  def mkSortable[T: ClassTag](fName: String): Term.Apply = {
    val termType = Type.Name("Sortable")
    val termTypeParamType = inferTypeParam[T]
    q"""
        new ${scala.meta.Init(termType, termType, Seq.empty)}[${termTypeParamType}] {
          val fieldName = $fName
          def desc(): com.yandex.yoctodb.query.Order = QueryBuilder.desc(fieldName)
          def asc(): com.yandex.yoctodb.query.Order = QueryBuilder.asc(fieldName)
        }
      """
  }

  def inferTypeParam[T: ClassTag]: Type.Name = {
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

/*

https://scalameta.org/docs/trees/guide.html


val vals =
      List(
        Decl.Val(
          mods = Nil,
          pats = List(Pat.Var(name = Term.Name("a"))),
          decltpe = Type.Name("Int"),
        ),
        Decl.Val(
          mods = Nil,
          pats = List(Pat.Var(name = Term.Name("b"))),
          decltpe = Type.Name("Int"),
        ),
      )

    // param"..$mods $name: $tpeopt = $expropt"
    /*Term.Param.apply(
      mods = Nil,
      name = meta.Name("homeTeam"),
      tpeopt = ???,
      decltpe = Some(scala.meta.Type.Name("Int"))
    )*/

    // https://scalameta.org/docs/trees/examples.html
    // param"val homeTeam: Int = 2".parse[scala.meta.Stat]

    println("val homeTeam: Int = 2".parse[Stat].get.structure)

    Defn.Val(
      Nil,
      List(Pat.Var(Term.Name("homeTeam"))),
      Some(Type.Name("Int")),
      Lit.Int(2),
    )

    // $name[..$tparams]
    "null".parse[Term].get.structure

    // tparam"Filterable, String".parse[Term].get.structure
    // q"final case class $tname[..$tparams] ..$ctorMods (...$paramss) $template"
    // println(homeTeam.parse[Stat].get.structure)



/*
 */
    // ${mkFilterableStrTerm2(scala.meta.Lit.String("homeTeam"), Type.Name("Filterable"), Type.Name(classOf[String].getSimpleName()))}
    Source(
      stats = List(
        Defn.Class(
          mods = Nil,
          name = Type.Name("SearchIndexQueryDsl"),
          tparams = Nil,
          ctor = Ctor.Primary(
            origin = scala.meta.trees.Origin.None,
            mods = Nil,
            name = Name.Anonymous(),
            // paramss = List(Term.ParamClause(pms))
            paramss = List(pms),
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
      )
    )

    Term.ParamClause(pms)

    // println(q"function(argument)".structure)
    Term.Apply(
      Term.Name("function"),
      Term.ArgClause(
        List(
          Term.Name("argument")
        )
      ),
    )

    // q"def this(...${homeTeam})"
    q"def this($a, $b)" // .parse[meta.Ctor.Primary]

    // Term.ArgClause(pms)

    meta.Init(
      tpe = Type.Name("Filterable"),
      name = Type.Name(classOf[String].getSimpleName()),
      Seq.empty,
    )

    /*
    val program = """object Main extends App { print("Hello!") }"""
    val tree = program.parse[Source].get
     println(tree.syntax)
 */


 val a = param"""val homeTeam: String = "1111" """
    val b = param"val awayTeam: Int = 42"
    val pms = List(a, b)

 */
