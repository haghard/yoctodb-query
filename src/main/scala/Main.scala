import com.yandex.yoctodb.DatabaseFormat
import com.yandex.yoctodb.immutable.Database
import com.yandex.yoctodb.query.QueryBuilder as yocto
import com.yandex.yoctodb.util.buf.Buffer
import com.yandex.yoctodb.v1.immutable.V1Database
import org.slf4j.LoggerFactory

import java.nio.file.Paths
import scala.util.Try
import query.dsl.*

import java.util.concurrent.{ CountDownLatch, TimeUnit }
import scala.util.control.NonFatal

object Program extends App {
  val logger = LoggerFactory.getLogger("app")

  val (ht, at, stage, gTs, winner, yy, mm, dd) = Accessor[Index].columns

  val query =
    yocto
      .select
      .where(
        yocto.and(
          // stage.in(Set("season-18-19", "season-19-20")),
          // stage.in(Set("season-23-24", "season-24-25")),
          stage.in(Set("season-24-25")),
          // winner.=!=("lal"),
          winner.=:=("lal"),
          // yocto.or(ht =:= "okc", at =:= "okc"),
          yocto.or(ht =:= "lal", at =:= "lal"),
        )
      )
      .orderBy(gTs.desc)

  def loadIndex(): Try[V1Database] =
    Try {
      val indexPath = "indexes/games"
      val indexFile = Paths.get(indexPath).toFile
      if (indexFile.exists() && indexFile.isFile()) {
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

  val cl = new CountDownLatch(1)

  loadIndex().map {
    searchIndex: V1Database =>
      try {
        val cnt =
          searchIndex.executeAndUnlimitedCount(
            query,
            (docId: Int, _: Database) => {
              // val payload: com.yandex.yoctodb.util.buf.Buffer = searchIndex.getFieldValue(docId, "g_payload")
              // val result = NbaResultPB.parseFrom(new com.yandex.yoctodb.util.buf.BufferInputStream(payload))
              // logger.debug(s"DocId: $docId ${payload.toByteArray.length}")
              logger.debug(s"DocId: $docId")
              true
            },
          )
        Thread.sleep(100)
        logger.debug("★ ★ ★ ResultSet:{} docs", cnt)
      }
      catch {
        case NonFatal(ex) => ex.printStackTrace()
      }
      finally cl.countDown()
  }

  cl.await(10, TimeUnit.SECONDS)

}

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
