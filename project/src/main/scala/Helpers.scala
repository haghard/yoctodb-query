import scala.meta._

object Helpers {
  def filterable(termType: Type.Name, termTypeParamType: Type.Name): Defn.Val =
    q"""
      val $$: ${termType}[${termTypeParamType}] = new ${scala.meta.Init(termType, termType, Seq.empty)}[${termTypeParamType}] {
        def =:=(v: ${termTypeParamType}): TermCondition = QueryBuilder.eq(name, UnsignedByteArrays.from(v))
        def in(vs: scala.collection.immutable.Set[${termTypeParamType}]): TermCondition =
          QueryBuilder.in(name, vs.toSeq.map(UnsignedByteArrays.from(_)):_*)
      }
    """

  def sortable(termType: Type.Name, termTypeParamType: Type.Name): Defn.Val =
    q"""
      val $$: ${termType}[${termTypeParamType}] = new ${scala.meta.Init(termType, termType, Seq.empty)}[${termTypeParamType}] {
        def desc(): com.yandex.yoctodb.query.Order = QueryBuilder.desc(name)
        def asc(): com.yandex.yoctodb.query.Order = QueryBuilder.asc(name)
      }
    """

  def filterableNum(
      termType: Type.Name,
      termTypeParamType: Type.Name,
      isDouble: Boolean,
    ): Defn.Val =
    if (isDouble)
      q"""
      val $$: ${termType}[${termTypeParamType}] = new ${scala.meta.Init(termType, termType, Seq.empty)}[${termTypeParamType}] {

        def =:=(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.eq(name, UnsignedByteArrays.from(writeDouble(v)))

        def in(vs: scala.collection.immutable.Set[${termTypeParamType}]): TermCondition =
          QueryBuilder.in(name, vs.toSeq.map(d=>UnsignedByteArrays.from(writeDouble(d))):_*)

        def >(v: ${termTypeParamType}): TermCondition =
         QueryBuilder.gt(name, UnsignedByteArrays.from(writeDouble(v)))

        def >=(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.gte(name, UnsignedByteArrays.from(writeDouble(v)))

        def <(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.lt(name, UnsignedByteArrays.from(writeDouble(v)))

        def <=(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.lte(name, UnsignedByteArrays.from(writeDouble(v)))
      }
    """
    else
      q"""
      val $$: ${termType}[${termTypeParamType}] = new ${scala.meta.Init(termType, termType, Seq.empty)}[${termTypeParamType}] {

        def =:=(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.eq(name, UnsignedByteArrays.from(v))

        def in(vs: scala.collection.immutable.Set[${termTypeParamType}]): TermCondition =
          QueryBuilder.in(name, vs.toSeq.map(UnsignedByteArrays.from(_)):_*)

        def >(v: ${termTypeParamType}): TermCondition =
         QueryBuilder.gt(name, UnsignedByteArrays.from(v))

        def >=(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.gte(name, UnsignedByteArrays.from(v))

        def <(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.lt(name, UnsignedByteArrays.from(v))

        def <=(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.lte(name, UnsignedByteArrays.from(v))
      }
    """

  def both(
      termType: Type.Name,
      termTypeParamType: Type.Name,
    ): Defn.Val =
      q"""
        val $$: ${termType}[${termTypeParamType}] = new ${scala.meta.Init(termType, termType, Seq.empty)}[${termTypeParamType}] {
  
          def =:=(v: ${termTypeParamType}): TermCondition =
            QueryBuilder.eq(name, UnsignedByteArrays.from(v))
  
          def in(vs: scala.collection.immutable.Set[${termTypeParamType}]): TermCondition =
            QueryBuilder.in(name, vs.toSeq.map(UnsignedByteArrays.from(_)):_*)
  
          def desc(): com.yandex.yoctodb.query.Order = QueryBuilder.desc(name)
          def asc(): com.yandex.yoctodb.query.Order = QueryBuilder.asc(name)
        }
      """

  def bothNum(
      termType: Type.Name,
      termTypeParamType: Type.Name,
      isDouble: Boolean,
    ): Defn.Val =
    if (isDouble)
      q"""
      val $$: ${termType}[${termTypeParamType}] = new ${scala.meta.Init(termType, termType, Seq.empty)}[${termTypeParamType}] {

        def =:=(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.eq(name, UnsignedByteArrays.from(writeDouble(v)))

        def in(vs: scala.collection.immutable.Set[${termTypeParamType}]): TermCondition =
          QueryBuilder.in(name, vs.toSeq.map(d=>UnsignedByteArrays.from(writeDouble(d))):_*)

        def >(v: ${termTypeParamType}): TermCondition =
         QueryBuilder.gt(name, UnsignedByteArrays.from(writeDouble(v)))

        def >=(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.gte(name, UnsignedByteArrays.from(writeDouble(v)))

        def <(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.lt(name, UnsignedByteArrays.from(writeDouble(v)))

        def <=(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.lte(name, UnsignedByteArrays.from(writeDouble(v)))

        def desc(): com.yandex.yoctodb.query.Order = QueryBuilder.desc(name)
        def asc(): com.yandex.yoctodb.query.Order = QueryBuilder.asc(name)
      }
    """
    else
      q"""
        val $$: ${termType}[${termTypeParamType}] = new ${scala.meta.Init(termType, termType, Seq.empty)}[${termTypeParamType}] {
  
          def =:=(v: ${termTypeParamType}): TermCondition =
            QueryBuilder.eq(name, UnsignedByteArrays.from(v))
  
          def in(vs: scala.collection.immutable.Set[${termTypeParamType}]): TermCondition =
            QueryBuilder.in(name, vs.toSeq.map(UnsignedByteArrays.from(_)):_*)
  
          def >(v: ${termTypeParamType}): TermCondition =
           QueryBuilder.gt(name, UnsignedByteArrays.from(v))
  
          def >=(v: ${termTypeParamType}): TermCondition =
            QueryBuilder.gte(name, UnsignedByteArrays.from(v))
  
          def <(v: ${termTypeParamType}): TermCondition =
            QueryBuilder.lt(name, UnsignedByteArrays.from(v))
  
          def <=(v: ${termTypeParamType}): TermCondition =
            QueryBuilder.lte(name, UnsignedByteArrays.from(v))
  
          def desc(): com.yandex.yoctodb.query.Order = QueryBuilder.desc(name)
          def asc(): com.yandex.yoctodb.query.Order = QueryBuilder.asc(name)
        }
      """

}
