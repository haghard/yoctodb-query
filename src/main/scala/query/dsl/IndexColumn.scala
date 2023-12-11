package query.dsl

trait IndexColumn[A] {

  def fieldName: String

  def ops: Ops[A]

}
