package query.dsl

import zio.schema.DeriveSchema
import zio.schema.Schema

//TODO: Codegen this !!!

final case class IndexSchema(
    games_ht: String,
    games_at: String,
    games_stage: String,
    games_ts: Long,
    games_winner: String,
    games_yy: Long,
    games_mm: Long,
    games_dd: Long,
  )

object IndexSchema {
  implicit val schema: Schema.CaseClass8.WithFields[
    "games_ht",
    "games_at",
    "games_stage",
    "games_ts",
    "games_winner",
    "games_yy",
    "games_mm",
    "games_dd",
    String,
    String,
    String,
    Long,
    String,
    Long,
    Long,
    Long,
    IndexSchema,
  ] = DeriveSchema.gen[IndexSchema]

}
