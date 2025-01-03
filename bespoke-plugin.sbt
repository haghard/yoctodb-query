import _root_.sbt.Keys.*

import java.nio.file.Paths
import scala.util.{ Try, Using }
import scala.meta.*
import com.yandex.yoctodb.DatabaseFormat
import com.yandex.yoctodb.util.buf.Buffer
import com.yandex.yoctodb.v1.immutable.V1Database
import com.yandex.yoctodb.immutable.*

import scala.jdk.CollectionConverters.asScalaSetConverter

// The entrypoint of our project's source code generator
def emitSources(
    sourceManagedPath: java.io.File
  ): List[(scala.meta.Source, java.io.File)] = {
  // predefined/expected schema as a value
  val schema =
    List(
      ("Year", "games_yy", classOf[java.lang.Integer].getSimpleName(), t"FilterableNum"),
      ("Day", "games_dd", classOf[java.lang.Integer].getSimpleName(), t"FilterableNum"),
      ("Month", "games_mm", classOf[java.lang.Integer].getSimpleName(), t"FilterableNum"),
      ("GameStage", "games_stage", classOf[String].getSimpleName(), t"Filterable"),
      ("HomeTeam", "games_ht", classOf[String].getSimpleName(), t"Filterable"),
      ("AwayTeam", "games_at", classOf[String].getSimpleName(), t"Filterable"),
      ("Winner", "games_winner", classOf[String].getSimpleName(), t"Filterable"),
      ("GameTime", "games_ts", classOf[java.lang.Long].getSimpleName(), t"Sortable"),
    )

  val expectedFiltered =
    Set("games_yy", "games_dd", "games_mm", "games_stage", "games_ht", "games_at", "games_winner")

  val columns =
    schema.map { row =>
      val name = row._1
      q"Col(${scala.meta.Term.Name(name)}())"
    }

  loadIndex()
    .map {
      case (sorted, filtered) =>
        if (
            sorted.contains("games_ts") // sorted
            && (filtered.asScala.intersect(expectedFiltered) == expectedFiltered)
        )
          schema.map {
            case (name, columnName, tp, traitToImpl) =>
              (
                generate(name, columnName, tp, traitToImpl),
                sourceManagedPath / "query" / "dsl" / s"${name}.scala",
              )
          } :+ (genIndex(columns), sourceManagedPath / "query" / "dsl" / "SearchIndex.scala")
        else {
          println("Schema error !!!")
          List.empty
        }
    }
    .getOrElse(List.empty)
}

def genIndex(columns: List[Term]): scala.meta.Source = {
  val indexTerm = columns
    .foldLeft[Option[Term]](None) {
      case (None, column) =>
        Some(column)
      case (Some(index), column) =>
        Some(q"$index ++ $column")
    }
    .getOrElse(throw new Exception("Empty columns!"))
  source"""
    package query.dsl
    import query.dsl.Col._
    object SearchIndex {
      val index = ${indexTerm}
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

def generate(
    termClName: String,
    columnName: String,
    scalaType: String,
    columnTypeName: scala.meta.Type.Name,
  ): scala.meta.Source = {
  val term = Type.Name(termClName)
  val clmType = Type.Name(scalaType)
  val accessorParam = param"override val fieldName: String = $columnName"

  columnTypeName.value match {
    case "Filterable" =>
      source"""
        package query.dsl
        import com.yandex.yoctodb.query._
        import com.yandex.yoctodb.util.UnsignedByteArrays
        final case class ${term}(${accessorParam}) extends IndexColumn[$clmType] {
          ${CompanionFunctions.buildFilterableTerm(columnTypeName, clmType)}
        }
      """

    case "FilterableNum" =>
      source"""
        package query.dsl
        import com.yandex.yoctodb.query._
        import com.yandex.yoctodb.util.UnsignedByteArrays
        final case class ${term}(${accessorParam}) extends IndexColumn[$clmType] {
          ${CompanionFunctions.buildFilterableNumTerm(columnTypeName, clmType)}
        }
      """

    case "Sortable" =>
      source"""
        package query.dsl
        import com.yandex.yoctodb.query._
        final case class ${term}(${accessorParam}) extends IndexColumn[$clmType] {
          ${CompanionFunctions.buildSortableTerm(columnTypeName, clmType)}
        }
      """
  }
}

// A utility function to write scalameta ASTs into files
def writeFiles(outputs: List[(scala.meta.Source, java.io.File)]): List[java.io.File] = {
  import java.nio.file.Files
  import java.nio.file.Path
  import java.io.FileOutputStream
  outputs.map {
    case (src, dest) =>
      Files.createDirectories(Path.of(dest.getParent()))
      Using.resource(new FileOutputStream(dest)) { fos =>
        fos.write(src.syntax.getBytes())
      }
      println(s"Generated: $dest")
      dest
  }
}

lazy val generateTask = taskKey[List[java.io.File]]("Source-code-generating task")

generateTask := {
  // See SBT documentation on Caching to avoid triggering a recompilation:
  // https://www.scala-sbt.org/1.x/docs/Caching.html#Caching
  val sourceManagedPath = (Compile / sourceManaged).value
  writeFiles(emitSources(sourceManagedPath))
}

Compile / sourceGenerators += generateTask
