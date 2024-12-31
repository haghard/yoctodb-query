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

  val index = new SearchIndexQueryDsl()
  val query =
    yocto
      .select
      .where(
        yocto.and(
          index.gamesStage.in(Set("season-18-19", "season-19-20")),
          yocto.or(index.homeTeam =:= "lal", index.awayTeam =:= "lal"),
        )
      )
      .orderBy(index.gamesTs.desc())
    // .limit(10)

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
          searchIndex.executeAndUnlimitedCount( // execute
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
        logger.debug("★ ★ ★ Total: {} docs", cnt)
      }
      catch {
        case NonFatal(ex) => ex.printStackTrace()
      }
      finally cl.countDown()
  }

  cl.await(10, TimeUnit.SECONDS)

}
