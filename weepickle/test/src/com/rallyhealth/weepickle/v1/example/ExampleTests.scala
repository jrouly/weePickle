package com.rallyhealth.weepickle.v1.example

import acyclic.file
import com.rallyhealth.weepickle.v1.TestUtil
import utest._
import com.rallyhealth.weepickle.v1.WeePickle.{FromScala, ToScala, macroX, Transceiver => RW}
import com.rallyhealth.weejson.v1._
import com.rallyhealth.weejson.v1.jackson.{FromJson, ToJson, ToPrettyJson}
import com.rallyhealth.weepack.v1.{FromMsgPack, Msg, ToMsgPack, WeePack}
import com.rallyhealth.weepickle.v1.core.{NoOpVisitor, Visitor}
import com.rallyhealth.weepickle.v1.implicits.{discriminator, dropDefault}
object Simple {
  case class Thing(myFieldA: Int, myFieldB: String)
  object Thing{
    implicit val rw: RW[Thing] = macroX
  }
  case class Big(i: Int, b: Boolean, str: String, c: Char, t: Thing)
  object Big{
    implicit val rw: RW[Big] = macroX
  }
}
object Sealed{
  sealed trait IntOrTuple
  object IntOrTuple{
    implicit val rw: RW[IntOrTuple] = RW.merge(IntThing.rw, TupleThing.rw)
  }
  case class IntThing(i: Int) extends IntOrTuple
  object IntThing{
    implicit val rw: RW[IntThing] = macroX
  }
  case class TupleThing(name: String, t: (Int, Int)) extends IntOrTuple
  object TupleThing{
    implicit val rw: RW[TupleThing] = macroX
  }
}
object Recursive{
  case class Foo(i: Int)
  object Foo{
    implicit val rw: RW[Foo] = macroX
  }
  case class Bar(name: String, foos: Seq[Foo])
  object Bar{
    implicit val rw: RW[Bar] = macroX
  }
}
object Defaults{
  case class FooOmitDefault(
    @dropDefault i: Int = 10,
    @dropDefault s: String = "lol")
  object FooOmitDefault{
    implicit val rw: RW[FooOmitDefault] = macroX
  }
  case class FooIncludeDefault(i: Int = 10, s: String = "lol")
  object FooIncludeDefault{
    implicit val rw: RW[FooIncludeDefault] = macroX
  }
}
object Keyed{
  case class KeyBar(@com.rallyhealth.weepickle.v1.implicits.key("hehehe") kekeke: Int)
  object KeyBar{
    implicit val rw: RW[KeyBar] = macroX
  }
}
object KeyedTag{
  @discriminator("customDiscriminator")
  sealed trait A
  object A{
    implicit val rw: RW[A] = RW.merge(B.rw, macroX[C.type])
  }
  @com.rallyhealth.weepickle.v1.implicits.key("Bee") case class B(i: Int) extends A
  object B{
    implicit val rw: RW[B] = macroX
  }
  case object C extends A
}
object Custom2{
  class CustomThing2(val i: Int, val s: String)
  object CustomThing2 {
    implicit val rw = com.rallyhealth.weepickle.v1.WeePickle.readerTransmitter[String].bimap[CustomThing2](
      x => x.i + " " + x.s,
      str => {
        val Array(i, s) = str.split(" ", 2)
        new CustomThing2(i.toInt, s)
      }
    )
  }
}

import KeyedTag._
import Keyed._
import Sealed._
import Simple._
import Recursive._
import Defaults._

object ExampleTests extends TestSuite {

  import TestUtil._
  val tests = Tests {
    test("simple") {
      import com.rallyhealth.weepickle.v1.WeePickle._

      FromScala(1).transmit(ToJson.string) ==> "1"

      FromScala(Seq(1, 2, 3)).transmit(ToJson.string) ==> "[1,2,3]"

      FromJson("[1,2,3]").transmit(ToScala[Seq[Int]]) ==> List(1, 2, 3)

      FromScala((1, "omg", true)).transmit(ToJson.string) ==> """[1,"omg",true]"""

      FromJson("""[1,"omg",true]""").transmit(ToScala[(Int, String, Boolean)]) ==> (1, "omg", true)
    }
    test("binary"){
      import com.rallyhealth.weepickle.v1.WeePickle._

      FromScala(1).transmit(ToMsgPack.bytes)  ==> Array(1)

      FromScala(Seq(1, 2, 3)).transmit(ToMsgPack.bytes)               ==> Array(0x93.toByte, 1, 2, 3)

      FromMsgPack(Array[Byte](0x93.toByte, 1, 2, 3)).transmit(ToScala[Seq[Int]])  ==> List(1, 2, 3)

      val serializedTuple = Array[Byte](0x93.toByte, 1, 0xa3.toByte, 111, 109, 103, 0xc3.toByte)

      FromScala((1, "omg", true)).transmit(ToMsgPack.bytes)           ==> serializedTuple

      FromMsgPack(serializedTuple).transmit(ToScala[(Int, String, Boolean)]) ==> (1, "omg", true)
    }
    test("more"){
      import com.rallyhealth.weepickle.v1.WeePickle._
      test("booleans"){
       FromScala(true: Boolean).transmit(ToJson.string)              ==> "true"
       FromScala(false: Boolean).transmit(ToJson.string)             ==> "false"
      }
      test("numbers"){
       FromScala(12: Int).transmit(ToJson.string)                    ==> "12"
       FromScala(12: Short).transmit(ToJson.string)                  ==> "12"
       FromScala(12: Byte).transmit(ToJson.string)                   ==> "12"
       FromScala(Int.MaxValue).transmit(ToJson.string)               ==> "2147483647"
       FromScala(Int.MinValue).transmit(ToJson.string)               ==> "-2147483648"
       FromScala(12.5f: Float).transmit(ToJson.string)               ==> "12.5"
       FromScala(12.5: Double).transmit(ToJson.string)               ==> "12.5"
      }
      test("longs"){
       FromScala(12: Long).transmit(ToJson.string)                   ==> "12"
       FromScala(4000000000000L: Long).transmit(ToJson.string)       ==> "4000000000000"
        // large longs are written as strings, to avoid floating point rounding
       FromScala(9223372036854775807L: Long).transmit(ToJson.string) ==> "9223372036854775807"
      }
      test("specialNumbers"){
       FromScala(1.0/0: Double).transmit(ToJson.string)              ==> "\"Infinity\""
       FromScala(Float.PositiveInfinity).transmit(ToJson.string)     ==> "\"Infinity\""
       FromScala(Float.NegativeInfinity).transmit(ToJson.string)     ==> "\"-Infinity\""
      }
      test("charStrings"){
       FromScala('o').transmit(ToJson.string)                        ==> "\"o\""
       FromScala("omg").transmit(ToJson.string)                      ==> "\"omg\""
      }
      test("seqs"){
       FromScala(Array(1, 2, 3)).transmit(ToJson.string)             ==> "[1,2,3]"

        // You can pass in an `indent` parameter to format it nicely
       FromScala(Array(1, 2, 3)).transmit(ToPrettyJson.string)  ==>
          """[
            |  1,
            |  2,
            |  3
            |]""".stripMargin

       FromScala(Seq(1, 2, 3)).transmit(ToJson.string)               ==> "[1,2,3]"
       FromScala(Vector(1, 2, 3)).transmit(ToJson.string)            ==> "[1,2,3]"
       FromScala(List(1, 2, 3)).transmit(ToJson.string)              ==> "[1,2,3]"
        import collection.immutable.SortedSet
       FromScala(SortedSet(1, 2, 3)).transmit(ToJson.string)         ==> "[1,2,3]"
      }
      test("options"){
       FromScala(Some(1)).transmit(ToJson.string)                    ==> "1"
       FromScala(None).transmit(ToJson.string)                       ==> "null"
      }
      test("tuples"){
       FromScala((1, "omg")).transmit(ToJson.string)                 ==> """[1,"omg"]"""
       FromScala((1, "omg", true)).transmit(ToJson.string)           ==> """[1,"omg",true]"""
      }

      test("caseClass"){
        import com.rallyhealth.weepickle.v1._
       FromScala(Thing(1, "gg")).transmit(ToJson.string)             ==> """{"myFieldA":1,"myFieldB":"gg"}"""
        FromJson("""{"myFieldA":1,"myFieldB":"gg"}""").transmit(ToScala[Thing]) ==> Thing(1, "gg")
       FromScala(Big(1, true, "lol", 'Z', Thing(7, ""))).transmit(ToJson.string) ==>
          """{"i":1,"b":true,"str":"lol","c":"Z","t":{"myFieldA":7,"myFieldB":""}}"""

       FromScala(Big(1, true, "lol", 'Z', Thing(7, ""))).transmit(ToPrettyJson.string) ==>
          """{
            |  "i": 1,
            |  "b": true,
            |  "str": "lol",
            |  "c": "Z",
            |  "t": {
            |    "myFieldA": 7,
            |    "myFieldB": ""
            |  }
            |}""".stripMargin
        }


      test("sealed"){
       FromScala(IntThing(1)).transmit(ToJson.string) ==> """{"$type":"com.rallyhealth.weepickle.v1.example.Sealed.IntThing","i":1}"""

       FromScala(TupleThing("naeem", (1, 2))).transmit(ToJson.string) ==>
          """{"$type":"com.rallyhealth.weepickle.v1.example.Sealed.TupleThing","name":"naeem","t":[1,2]}"""

        // You can read tagged value without knowing its
        // type in advance, just use type of the sealed trait
        FromJson("""{"$type":"com.rallyhealth.weepickle.v1.example.Sealed.IntThing","i":1}""").transmit(ToScala[IntOrTuple]) ==> IntThing(1)

      }
      test("recursive"){
       FromScala((((1, 2), (3, 4)), ((5, 6), (7, 8)))).transmit(ToJson.string) ==>
          """[[[1,2],[3,4]],[[5,6],[7,8]]]"""

       FromScala(Seq(Thing(1, "g"), Thing(2, "k"))).transmit(ToJson.string) ==>
          """[{"myFieldA":1,"myFieldB":"g"},{"myFieldA":2,"myFieldB":"k"}]"""

       FromScala(Bar("bearrr", Seq(Foo(1), Foo(2), Foo(3)))).transmit(ToJson.string) ==>
          """{"name":"bearrr","foos":[{"i":1},{"i":2},{"i":3}]}"""

      }
      test("null"){
       FromScala(Bar(null, Seq(Foo(1), null, Foo(3)))).transmit(ToJson.string) ==>
          """{"name":null,"foos":[{"i":1},null,{"i":3}]}"""
      }
    }
    test("defaults"){
      import com.rallyhealth.weepickle.v1.WeePickle._
      test("omit") {
        // lihaoyi/upickle default behavior
        test("reading is tolerant"){
          FromJson("{}").transmit(ToScala[FooOmitDefault])                ==> FooOmitDefault(10, "lol")
          FromJson("""{"i": 123}""").transmit(ToScala[FooOmitDefault])    ==> FooOmitDefault(123,"lol")
        }
        test("writing omits defaults"){
         FromScala(FooOmitDefault(i = 11, s = "lol")).transmit(ToJson.string)  ==> """{"i":11}"""
         FromScala(FooOmitDefault(i = 10, s = "lol")).transmit(ToJson.string)  ==> """{}"""
         FromScala(FooOmitDefault()).transmit(ToJson.string)                   ==> """{}"""
        }
      }
      test("include") {
        // rallyhealth/weepickle default behavior
        test("reading is tolerant"){
          FromJson("{}").transmit(ToScala[FooIncludeDefault])                ==> FooIncludeDefault(10, "lol")
          FromJson("""{"i": 123}""").transmit(ToScala[FooIncludeDefault])    ==> FooIncludeDefault(123,"lol")
        }
        test("writing includes defaults"){
         FromScala(FooIncludeDefault(i = 11, s = "lol")).transmit(ToJson.string)  ==> """{"i":11,"s":"lol"}"""
         FromScala(FooIncludeDefault(i = 10, s = "lol")).transmit(ToJson.string)  ==> """{"i":10,"s":"lol"}"""
         FromScala(FooIncludeDefault()).transmit(ToJson.string)                   ==> """{"i":10,"s":"lol"}"""
        }
      }

    }

    test("sources"){
      import com.rallyhealth.weepickle.v1.WeePickle._
      val original = """{"myFieldA":1,"myFieldB":"gg"}"""
      FromJson(original).transmit(ToScala[Thing]) ==> Thing(1, "gg")
      FromJson(original.getBytes).transmit(ToScala[Thing]) ==> Thing(1, "gg")
    }
    test("mapped"){
      test("simple"){
        import com.rallyhealth.weepickle.v1.WeePickle._
        case class Wrap(i: Int)
        implicit val fooReadWrite: Transceiver[Wrap] =
          readerTransmitter[Int].bimap[Wrap](_.i, Wrap(_))

       FromScala(Seq(Wrap(1), Wrap(10), Wrap(100))).transmit(ToJson.string) ==> "[1,10,100]"
        FromJson("[1,10,100]").transmit(ToScala[Seq[Wrap]]) ==> Seq(Wrap(1), Wrap(10), Wrap(100))
      }
      test("Value"){
        import com.rallyhealth.weepickle.v1.WeePickle._
        case class Bar(i: Int, s: String)
        implicit val fooReadWrite: Transceiver[Bar] =
          readerTransmitter[com.rallyhealth.weejson.v1.Value].bimap[Bar](
            x => com.rallyhealth.weejson.v1.Arr(x.s, x.i),
            json => new Bar(json(1).num.toInt, json(0).str)
          )

       FromScala(Bar(123, "abc")).transmit(ToJson.string) ==> """["abc",123]"""
        FromJson("""["abc",123]""").transmit(ToScala[Bar]) ==> Bar(123, "abc")
      }
    }
    test("keyed"){
      import com.rallyhealth.weepickle.v1.WeePickle._
      test("attrs"){
       FromScala(KeyBar(10)).transmit(ToJson.string)                     ==> """{"hehehe":10}"""
        FromJson("""{"hehehe": 10}""").transmit(ToScala[KeyBar])    ==> KeyBar(10)
      }
      test("tag"){
       FromScala(B(10)).transmit(ToJson.string)                          ==> """{"customDiscriminator":"Bee","i":10}"""
        FromJson("""{"customDiscriminator":"Bee","i":10}""").transmit(ToScala[B]) ==> B(10)
      }
      test("snakeCase"){
        object SnakePickle extends com.rallyhealth.weepickle.v1.AttributeTagged{
          def camelToSnake(s: String) = {
            s.split("(?=[A-Z])", -1).map(_.toLowerCase).mkString("_")
          }
          def snakeToCamel(s: String) = {
            val res = s.split("_", -1).map(x => x(0).toUpper + x.drop(1)).mkString
            s(0).toLower + res.drop(1)
          }

          override def objectAttributeKeyReadMap(s: CharSequence) =
            snakeToCamel(s.toString)
          override def objectAttributeKeyWriteMap(s: CharSequence) =
            camelToSnake(s.toString)

          override def objectTypeKeyReadMap(s: CharSequence) =
            snakeToCamel(s.toString)
          override def objectTypeKeyWriteMap(s: CharSequence) =
            camelToSnake(s.toString)
        }

        // Default read-writing
        FromScala(Thing(1, "gg")).transmit(ToJson.string) ==>
          """{"myFieldA":1,"myFieldB":"gg"}"""

        FromJson("""{"myFieldA":1,"myFieldB":"gg"}""").transmit(ToScala[Thing]) ==>
          Thing(1, "gg")

        implicit def thingRW: SnakePickle.Transceiver[Thing] = SnakePickle.macroX

        // snake_case_keys read-writing
        SnakePickle.writer[Thing].transmit(Thing(1, "gg"), ToJson.string) ==>
          """{"my_field_a":1,"my_field_b":"gg"}"""

        FromJson("""{"my_field_a":1,"my_field_b":"gg"}""").transmit(SnakePickle.reader[Thing]) ==>
          Thing(1, "gg")
      }

      test("stringLongs"){
        FromScala(123: Long).transmit(ToJson.string) ==> "123"
        FromScala(Long.MaxValue).transmit(ToJson.string) ==> "9223372036854775807"

        object StringLongs extends com.rallyhealth.weepickle.v1.AttributeTagged{
          override implicit val LongTransmitter = new Transmitter[Long] {
            def transmit0[V](v: Long, out: Visitor[_, V]): V = out.visitString(v.toString)
          }
        }

        StringLongs.writer[Long].transmit(123: Long, ToJson.string) ==> "\"123\""
        StringLongs.writer[Long].transmit(Long.MaxValue, ToJson.string) ==> "\"9223372036854775807\""

        object NumLongs extends com.rallyhealth.weepickle.v1.AttributeTagged{
          override implicit val LongTransmitter = new Transmitter[Long] {
            def transmit0[V](v: Long, out: Visitor[_, V]): V = out.visitFloat64String(v.toString)
          }
        }

        NumLongs.writer[Long].transmit(123: Long, ToJson.string) ==> "123"
        NumLongs.writer[Long].transmit(Long.MaxValue, ToJson.string) ==> "9223372036854775807"

      }
    }

    test("transform"){
      FromScala(Foo(123)).transmit(ToScala[Foo]) ==> Foo(123)
      val big = Big(1, true, "lol", 'Z', Thing(7, ""))
      FromScala(big).transmit(ToScala[Big]) ==> big
    }
    test("msgConstruction"){
      val msg = com.rallyhealth.weepack.v1.Arr(
        com.rallyhealth.weepack.v1.Obj(com.rallyhealth.weepack.v1.Str("myFieldA") -> com.rallyhealth.weepack.v1.Int32(1), com.rallyhealth.weepack.v1.Str("myFieldB") -> com.rallyhealth.weepack.v1.Str("g")),
        com.rallyhealth.weepack.v1.Obj(com.rallyhealth.weepack.v1.Str("myFieldA") -> com.rallyhealth.weepack.v1.Int32(2), com.rallyhealth.weepack.v1.Str("myFieldB") -> com.rallyhealth.weepack.v1.Str("k"))
      )

      val binary: Array[Byte] = WeePack.write(msg)

      val read = WeePack.read(binary)
      assert(msg == read)
    }

    test("msgReadWrite"){
      val big = Big(1, true, "lol", 'Z', Thing(7, ""))
      val msg: com.rallyhealth.weepack.v1.Msg = FromScala(big).transmit(Msg)
      FromMsgPack(msg).transmit(ToScala[Big])  ==> big
    }

    test("msgInsideValue"){
      val msgSeq = Seq[com.rallyhealth.weepack.v1.Msg](
        com.rallyhealth.weepack.v1.Str("hello world"),
        com.rallyhealth.weepack.v1.Arr(com.rallyhealth.weepack.v1.Int32(1), com.rallyhealth.weepack.v1.Int32(2))
      )

      val binary: Array[Byte] = FromScala(msgSeq).transmit(ToMsgPack.bytes)

      FromMsgPack(binary).transmit(ToScala[Seq[com.rallyhealth.weepack.v1.Msg]]) ==> msgSeq
    }

    // TODO fix failing case. valid but we don't use msgpack, so we'll never encounter it.
    // TODO With jackson, I think we'd still have to encode the key as json, and add double quotes to the test's assertion.
//    test("msgToValue"){
//      val msg = com.rallyhealth.weepack.v1.Arr(
//        com.rallyhealth.weepack.v1.Obj(com.rallyhealth.weepack.v1.Str("myFieldA") -> com.rallyhealth.weepack.v1.Int32(1), com.rallyhealth.weepack.v1.Str("myFieldB") -> com.rallyhealth.weepack.v1.Str("g")),
//        com.rallyhealth.weepack.v1.Obj(com.rallyhealth.weepack.v1.Str("myFieldA") -> com.rallyhealth.weepack.v1.Int32(2), com.rallyhealth.weepack.v1.Str("myFieldB") -> com.rallyhealth.weepack.v1.Str("k"))
//      )
//
//      val binary: Array[Byte] = WeePack.write(msg)
//
//      // Can pretty-print starting from either the com.rallyhealth.weepack.v1.Msg structs,
//      // or the raw binary data
//      WeePack.transform(msg, com.rallyhealth.weejson.v1.StringRenderer()).toString ==>
//        """[{"myFieldA":1,"myFieldB":"g"},{"myFieldA":2,"myFieldB":"k"}]"""
//
//      WeePack.transform(binary, com.rallyhealth.weejson.v1.StringRenderer()).toString ==>
//        """[{"myFieldA":1,"myFieldB":"g"},{"myFieldA":2,"myFieldB":"k"}]"""
//
//      // Some messagepack structs cannot be converted to valid JSON, e.g.
//      // they may have maps with non-string keys. These can still be pretty-printed:
//      val msg2 = com.rallyhealth.weepack.v1.Obj(com.rallyhealth.weepack.v1.Arr(com.rallyhealth.weepack.v1.Int32(1), com.rallyhealth.weepack.v1.Int32(2)) -> com.rallyhealth.weepack.v1.Int32(1))
//      WeePack.transform(msg2, com.rallyhealth.weejson.v1.StringRenderer()).toString ==> """{[1,2]:1}"""
//    }
    test("json"){
      test("construction"){
        import com.rallyhealth.weejson.v1.Value

        val json0 = com.rallyhealth.weejson.v1.Arr(
          Obj("myFieldA" -> com.rallyhealth.weejson.v1.Num(1), "myFieldB" -> com.rallyhealth.weejson.v1.Str("g")),
          Obj("myFieldA" -> com.rallyhealth.weejson.v1.Num(2), "myFieldB" -> com.rallyhealth.weejson.v1.Str("k"))
        )

        val json = com.rallyhealth.weejson.v1.Arr( // The `com.rallyhealth.weejson.v1.Num` and `com.rallyhealth.weejson.v1.Str` calls are optional
          Obj("myFieldA" -> 1, "myFieldB" -> "g"),
          Obj("myFieldA" -> 2, "myFieldB" -> "k")
        )

        json0 ==> json
        json.toString ==> """[{"myFieldA":1,"myFieldB":"g"},{"myFieldA":2,"myFieldB":"k"}]"""

        val json2 = Obj(
          "hello" -> (0 until 5),
          "world" -> (0 until 5).map(i => (i.toString, i))
        )

        json2.toString ==> """{"hello":[0,1,2,3,4],"world":{"0":0,"1":1,"2":2,"3":3,"4":4}}"""
      }
      test("simple"){
        val str = """[{"myFieldA":1,"myFieldB":"g"},{"myFieldA":2,"myFieldB":"k"}]"""
        val json = WeeJson.read(str)
        json(0)("myFieldA").num   ==> 1
        json(0)("myFieldB").str   ==> "g"
        json(1)("myFieldA").num   ==> 2
        json(1)("myFieldB").str   ==> "k"

        WeeJson.write(json)         ==> str
      }
      test("mutable"){
        val str = """[{"myFieldA":1,"myFieldB":"g"},{"myFieldA":2,"myFieldB":"k"}]"""
        val json: com.rallyhealth.weejson.v1.Value = WeeJson.read(str)

        json.arr.remove(1)
        json(0)("myFieldA") = 1337
        json(0)("myFieldB") = json(0)("myFieldB").str + "lols"

        WeeJson.write(json) ==> """[{"myFieldA":1337,"myFieldB":"glols"}]"""
      }
      test("update"){
        val str = """[{"myFieldA":1,"myFieldB":"g"},{"myFieldA":2,"myFieldB":"k"}]"""
        val json: com.rallyhealth.weejson.v1.Value = WeeJson.read(str)

        json(0)("myFieldA") = _.num + 100
        json(1)("myFieldB") = _.str + "lol"

        val expected = """[{"myFieldA":101,"myFieldB":"g"},{"myFieldA":2,"myFieldB":"klol"}]"""
        WeeJson.write(json) ==> expected
      }
      test("intermediate"){
        val data = Seq(Thing(1, "g"), Thing(2, "k"))
        val json = FromScala(data).transmit(Value)

        json.arr.remove(1)
        json(0)("myFieldA") = 1337

        json.transmit(ToScala[Seq[Thing]])   ==> Seq(Thing(1337, "g"))
      }
      test("copy"){
        val data = Obj(
          "hello" -> 1,
          "world" -> 2
        )

        val data2 = WeeJson.copy(data)

        data("hello") = 3
        data2("hello").num ==> 1
      }
    }
    test("transforms"){
      test("json"){
        import com.rallyhealth.weepickle.v1.WeePickle._
        FromScala(1).transmit(ToScala[com.rallyhealth.weejson.v1.Value]) ==> com.rallyhealth.weejson.v1.Num(1)
        FromScala("hello").transmit(ToScala[com.rallyhealth.weejson.v1.Value]) ==> com.rallyhealth.weejson.v1.Str("hello")
        FromScala(("hello", 9)).transmit(ToScala[com.rallyhealth.weejson.v1.Value]) ==> com.rallyhealth.weejson.v1.Arr("hello", 9)
        FromScala(Thing(3, "3")).transmit(ToScala[com.rallyhealth.weejson.v1.Value]) ==>
          Obj("myFieldA" -> 3, "myFieldB" -> "3")

        FromScala(com.rallyhealth.weejson.v1.Num(1)).transmit(ToScala[Int]) ==> 1
        FromScala(com.rallyhealth.weejson.v1.Str("hello")).transmit(ToScala[String]) ==> "hello"
        FromScala(com.rallyhealth.weejson.v1.Arr("hello", 9)).transmit(ToScala[(String, Int)]) ==> ("hello", 9)
        FromScala(Obj("myFieldA" -> 3, "myFieldB" -> "3")).transmit(ToScala[Thing]) ==>
          Thing(3, "3")
      }

      test("defaultTransform"){

        // com.rallyhealth.weepickle.v1.WeePickle.transform can be used to convert between
        // JSON-equivalent data-structures without an intermediate AST
        FromScala(Seq(1, 2, 3)).transmit(ToScala[(Int, Int, Int)]) ==> (1, 2, 3)

        val bar = Bar("omg", Seq(Foo(1), Foo(2)))

        FromScala(bar).transmit(ToScala[Map[String, com.rallyhealth.weejson.v1.Value]]) ==>
          Map[String, com.rallyhealth.weejson.v1.Value](
            "name" -> "omg",
            "foos" -> com.rallyhealth.weejson.v1.Arr(
              Obj("i" -> 1),
              Obj("i" -> 2)
            )
          )

      }
      test("misc"){
        // It can be used for parsing JSON into an AST
        val exampleAst = com.rallyhealth.weejson.v1.Arr(1, 2, 3)

        WeeJson.transform("[1, 2, 3]", Value) ==> exampleAst

        // Rendering the AST to a string

        exampleAst.transmit(StringRenderer()).toString ==> "[1,2,3]"

        // Or to a byte array
        exampleAst.transmit(BytesRenderer()).toByteArray ==> "[1,2,3]".getBytes

        // Re-formatting JSON, either compacting it
        WeeJson.transform("[1, 2, 3]", StringRenderer()).toString ==> "[1,2,3]"

        // or indenting it
        WeeJson.transform("[1, 2, 3]", StringRenderer(indent = 4)).toString ==>
          """[
            |    1,
            |    2,
            |    3
            |]""".stripMargin

        // `transform` takes any `Transformable`, including byte arrays and files
        WeeJson.transform("[1, 2, 3]".getBytes, StringRenderer()).toString ==> "[1,2,3]"

      }
      test("validate"){
        WeeJson.transform("[1, 2, 3]", NoOpVisitor)

        intercept[Exception](
          WeeJson.transform("[1, 2, 3", NoOpVisitor)
        )
        intercept[Exception](
          WeeJson.transform("[1, 2, 3]]", NoOpVisitor)
        )
      }
      test("com.rallyhealth.weepickle.v1.Default"){
        WeeJson.transform("[1, 2, 3]", com.rallyhealth.weepickle.v1.WeePickle.reader[Seq[Int]]) ==>
          Seq(1, 2, 3)

        FromScala(Seq(1, 2, 3)).transmit(ToJson.string) ==>  "[1,2,3]"
      }
    }
    test("byteArrays"){
      import com.rallyhealth.weepickle.v1.WeePickle._
      /**
        * JSON encoding isn't symmetric here,
        * but base64 is more useful.
        * e.g. https://stackoverflow.com/a/247261
        */
     FromScala(Array[Byte](1, 2, 3, 4)).transmit(ToJson.string) ==> """"AQIDBA==""""
      FromJson("[1,2,3,4]").transmit(ToScala[Array[Byte]]) ==> Array(1, 2, 3, 4)

      FromScala(Array[Byte](1, 2, 3, 4)).transmit(ToMsgPack.bytes) ==> Array(0xc4.toByte, 4, 1, 2, 3, 4)
      FromMsgPack(Array[Byte](0xc4.toByte, 4, 1, 2, 3, 4)).transmit(ToScala[Array[Byte]]) ==> Array(1, 2, 3, 4)
    }
  }
}


