package query.dsl

import com.yandex.yoctodb.immutable.Database
import scala.util.*
import com.yandex.yoctodb.query.QueryBuilder as yocto

//runMain query.dsl.QueryGameStat
object QueryGameStat extends App with IndexLoader {
  loadIndex("indexes/gameStat") match {
    case Success(gameStatIndex) =>

      val qLastN =
        yocto
          .select
          .where(
            yocto.and(
              gameStat.teams_name.$.=:=("sas"),
              gameStat.teams_stage.$.=:=("playoff-25-26"),
            )
          )
          .orderBy(gameStat.teams_ts.$.desc())
          .limit(10)

      /*
      val qTopfgPct =
        yocto
          .select
          .where(
            yocto.and(
              gameStat.teams_name.$.=:=("lal"),
              gameStat.teams_stage.$.=:=("season-25-26"),
            )
          )
          .orderBy(gameStat.teams_fgPct.$.desc())
          .limit(10)

      val qTopPts =
        yocto
          .select
          .where(
            yocto.and(
              gameStat.teams_name.$.=:=("lal"),
              gameStat.teams_stage.$.=:=("season-25-26"),
            )
          )
          .orderBy(gameStat.teams_pts.$.desc())
          .limit(10)

      val topPtc =
        yocto
          .select
          .where(gameStat.teams_stage.$.=:=("season-25-26"))
          .orderBy(gameStat.teams_pts.$.desc())
          .limit(10)
       */

      gameStatIndex.execute(
        qLastN,
        (docId: Int, db: Database) => {
          val gameId = db.getFieldValue(docId, "team_game_fk")
          val payload = db.getFieldValue(docId, "team_payload")
          val opponent = db.getFieldValue(docId, "opponent")
          val statPb = basket
            .domain
            .v1
            .TotalPB
            .parseFrom(new com.yandex.yoctodb.util.buf.BufferInputStream(payload))

          logger.debug(
            s"Game_id: ${new String(gameId.toByteArray)} opponent:${new String(opponent.toByteArray)} Stats:${statPb.toString}"
          )
          true
        },
      )

    case Failure(ex) =>
      ex.printStackTrace()
  }

}
