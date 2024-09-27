import com.yandex.yoctodb.DatabaseFormat
import com.yandex.yoctodb.immutable.Database
import com.yandex.yoctodb.query.{ QueryBuilder => yocto }
import com.yandex.yoctodb.util.buf.Buffer
import com.yandex.yoctodb.v1.immutable.V1Database
import org.slf4j.LoggerFactory

import java.nio.file.Paths
import scala.util.Try
import query.dsl._
import Col._

object Program extends App {
  val logger = LoggerFactory.getLogger("app")

  import SearchIndex._

  val query =
    yocto
      .select
      .where(
        yocto.and(
          index.column[GameStage].ops.in(Set("season-18-19", "season-19-20")),
          yocto.or(index.column[HomeTeam].ops =:= "lal", index.column[AwayTeam].ops =:= "lal"),
        )
      )
      .orderBy(index.column[GameTime].ops.desc())
      .limit(10)

  def loadIndex(): Try[V1Database] =
    Try {
      val indexPath = "indexes/games"
      val indexFile = Paths.get(indexPath).toFile
      if (indexFile.exists && indexFile.isFile) {
        val reader = DatabaseFormat.getCurrent().getDatabaseReader()
        val db = reader.from(Buffer.mmap(indexFile, false)).asInstanceOf[V1Database]
        logger.warn(
          "★ ★ ★    Index size: {} MB. Docs number: {}  ★ ★ ★",
          indexFile.length() / (1024 * 1024),
          db.getDocumentCount(),
        )
        db
      }
      else throw new Exception(s"Couldn't find or open file $indexPath")
    }

  loadIndex().map {
    searchIndex: V1Database =>
      searchIndex.execute(
        query,
        (docId: Int, _: Database) => {
          val payload: com.yandex.yoctodb.util.buf.Buffer =
            searchIndex.getFieldValue(docId, "g_payload")
          // val result = NbaResultPB.parseFrom(new com.yandex.yoctodb.util.buf.BufferInputStream(payload))
          logger.debug(s"DocId: $docId ${payload.toByteArray.length}")
          true
        },
      )
  }

}

//Try table encoding
//https://www.slideshare.net/JaroslavRegec1/peeking-inside-the-engine-of-zio-sqlpdf

/*
edsl4
object Terms {

  sealed trait TermOps[T]
  object Empty extends TermOps[Nothing]

  trait FilterableOps[T] extends TermOps[T] {
    def eq$(v: T): Unit
    def notEq$(v: T): Unit
  }

  trait SetOps[T] extends TermOps[T] {
    def in$(vs: Set[T]): Unit
  }

  trait TypedTermOps[T <: String & Singleton] {
    type A
    type Term <: TermOps[A]
    def ops: Term
  }

  object TypedTermOps {

    given name: TypedTermOps["name"] with {
      type A = String
      type Term = FilterableOps[A]
      def ops: Term = new FilterableOps[A] {
        def eq$(v: A): Unit = println(s"$v eq")
        def notEq$(v: A): Unit = println(s"$v notEq")
      }
    }

    given age: TypedTermOps["age"] with {
      type A = Int
      type Term = SetOps[Int]
      def ops: Term = (vs: Set[Int]) => println(s"$vs in")
    }
  }

  // end TypedTermOps

  def term(name: String)(using TTC: TypedTermOps[name.type]): TTC.Term = TTC.ops

  term("name").eq$("aa")
  term("name").notEq$("aa")
  term("age").in$(Set(1, 2))
}
 */
