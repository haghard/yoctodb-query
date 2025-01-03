package query.dsl

sealed trait Ops[T]

trait Filterable[T] extends Ops[T] {
  def =:=(v: T): com.yandex.yoctodb.query.TermCondition

  def not_=:=(v: T): com.yandex.yoctodb.query.Condition =
    com.yandex.yoctodb.query.QueryBuilder.not(=:=(v))

  def in(vs: scala.collection.immutable.Set[T]): com.yandex.yoctodb.query.TermCondition

}

trait FilterableNum[T] extends Filterable[T] {
  def >(v: T): com.yandex.yoctodb.query.TermCondition

  def >=(v: T): com.yandex.yoctodb.query.TermCondition

  def <(v: T): com.yandex.yoctodb.query.TermCondition

  def <=(v: T): com.yandex.yoctodb.query.TermCondition

}

trait Sortable[T] extends Ops[T] {
  def desc(): com.yandex.yoctodb.query.Order

  def asc(): com.yandex.yoctodb.query.Order

}
