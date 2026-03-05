import com.yandex.yoctodb.DatabaseFormat
import com.yandex.yoctodb.immutable.Database
import com.yandex.yoctodb.query.QueryBuilder as yocto
import com.yandex.yoctodb.util.buf.Buffer
import com.yandex.yoctodb.v1.immutable.V1Database
import org.slf4j.LoggerFactory

import java.nio.file.Paths
import scala.util.{ Failure, Success, Try }
import query.dsl.*

object Program extends App {
  val logger = LoggerFactory.getLogger("app")

  val (homeTeam, visitorTeam, stage, winner, yyyy, dd, mm, gameTime) =
    Accessor[DeriveSchemaIndex].columns

  val query =
    yocto
      .select
      .where(
        yocto.and(
          // stage.in(Set("season-18-19", "season-19-20")),
          // stage.in(Set("season-23-24", "season-24-25")),
          stage.in(Set("season-24-25", "playoff-24-25")),
          // stage.in(Set("season-25-26")),
          // winner.=!=("lal"),
          // winner.=:=("lal"),
          yocto.or(homeTeam =:= "lal", visitorTeam =:= "lal"),
          // yocto.or(ht =:= "lal", at =:= "lal"),
        )
      )
      .orderBy(gameTime.desc)

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

  loadIndex() match {
    case Success(searchIndex: V1Database) =>
      val count =
        searchIndex.executeAndUnlimitedCount(
          query,
          (docId: Int, _: Database) => {
            // val payload: com.yandex.yoctodb.util.buf.Buffer = searchIndex.getFieldValue(docId, "g_payload")
            // val result = NbaResultPB.parseFrom(new com.yandex.yoctodb.util.buf.BufferInputStream(payload))
            // logger.debug(s"DocId: $docId ${payload.toByteArray.length}")
            logger.debug(s"doc-id: $docId")
            true
          },
        )
      logger.debug("★ ★ ★ ResultSet:{} docs", count)
    case Failure(ex) =>
      ex.printStackTrace()
  }

}
