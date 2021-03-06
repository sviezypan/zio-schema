package zio.schema.codec

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{
  DayOfWeek,
  Duration,
  Instant,
  LocalDate,
  LocalDateTime,
  LocalTime,
  Month,
  MonthDay,
  OffsetDateTime,
  OffsetTime,
  Period,
  Year,
  YearMonth,
  ZoneId,
  ZoneOffset,
  ZonedDateTime
}

import scala.util.Try

import zio.schema.Schema.Primitive
import zio.schema.{ Schema, StandardType }
import zio.stream.{ ZSink, ZStream }
import zio.test.Assertion._
import zio.test._
import zio.{ Chunk, ZIO }

// TODO: use generators instead of manual encode/decode
object ProtobufCodecSpec extends DefaultRunnableSpec {

  def spec = suite("ProtobufCodec Spec")(
    suite("Should correctly encode")(
      testM("integers") {
        for {
          e  <- encode(schemaBasicInt, BasicInt(150)).map(toHex)
          e2 <- encodeNS(schemaBasicInt, BasicInt(150)).map(toHex)
        } yield assert(e)(equalTo("089601")) && assert(e2)(equalTo("089601"))
      },
      testM("strings") {
        for {
          e  <- encode(schemaBasicString, BasicString("testing")).map(toHex)
          e2 <- encodeNS(schemaBasicString, BasicString("testing")).map(toHex)
        } yield assert(e)(equalTo("0A0774657374696E67")) && assert(e2)(equalTo("0A0774657374696E67"))
      },
      testM("floats") {
        for {
          e  <- encode(schemaBasicFloat, BasicFloat(0.001f)).map(toHex)
          e2 <- encodeNS(schemaBasicFloat, BasicFloat(0.001f)).map(toHex)
        } yield assert(e)(equalTo("0D6F12833A")) && assert(e2)(equalTo("0D6F12833A"))
      },
      testM("doubles") {
        for {
          e  <- encode(schemaBasicDouble, BasicDouble(0.001)).map(toHex)
          e2 <- encodeNS(schemaBasicDouble, BasicDouble(0.001)).map(toHex)
        } yield assert(e)(equalTo("09FCA9F1D24D62503F")) && assert(e2)(equalTo("09FCA9F1D24D62503F"))
      },
      testM("embedded messages") {
        for {
          e  <- encode(schemaEmbedded, Embedded(BasicInt(150))).map(toHex)
          e2 <- encodeNS(schemaEmbedded, Embedded(BasicInt(150))).map(toHex)
        } yield assert(e)(equalTo("0A03089601")) && assert(e2)(equalTo("0A03089601"))
      },
      testM("packed lists") {
        for {
          e  <- encode(schemaPackedList, PackedList(List(3, 270, 86942))).map(toHex)
          e2 <- encodeNS(schemaPackedList, PackedList(List(3, 270, 86942))).map(toHex)
        } yield assert(e)(equalTo("0A06038E029EA705")) && assert(e2)(equalTo("0A06038E029EA705"))
      },
      testM("unpacked lists") {
        for {
          e  <- encode(schemaUnpackedList, UnpackedList(List("foo", "bar", "baz"))).map(toHex)
          e2 <- encodeNS(schemaUnpackedList, UnpackedList(List("foo", "bar", "baz"))).map(toHex)
        } yield assert(e)(equalTo("0A03666F6F0A036261720A0362617A")) && assert(e2)(
          equalTo("0A03666F6F0A036261720A0362617A")
        )
      },
      testM("records") {
        for {
          e  <- encode(schemaRecord, Record("Foo", 123)).map(toHex)
          e2 <- encodeNS(schemaRecord, Record("Foo", 123)).map(toHex)
        } yield assert(e)(equalTo("0A03466F6F107B")) && assert(e2)(equalTo("0A03466F6F107B"))
      },
      testM("enumerations") {
        for {
          e  <- encode(schemaEnumeration, Enumeration(IntValue(482))).map(toHex)
          e2 <- encodeNS(schemaEnumeration, Enumeration(IntValue(482))).map(toHex)
        } yield assert(e)(equalTo("10E203")) && assert(e2)(equalTo("10E203"))
      },
      testM("failure") {
        for {
          e  <- encode(schemaFail, StringValue("foo")).map(_.size)
          e2 <- encodeNS(schemaFail, StringValue("foo")).map(_.size)
        } yield assert(e)(equalTo(0)) && assert(e2)(equalTo(0))
      }
    ),
    suite("Should successfully encode and decode")(
      testM("messages") {
        for {
          ed  <- encodeAndDecode(schema, message)
          ed2 <- encodeAndDecodeNS(schema, message)
        } yield assert(ed)(equalTo(Chunk(message))) && assert(ed2)(equalTo(message))
      },
      testM("booleans") {
        val value = true
        for {
          ed  <- encodeAndDecode(Schema[Boolean], value)
          ed2 <- encodeAndDecodeNS(Schema[Boolean], value)
        } yield assert(ed)(equalTo(Chunk(value))) && assert(ed2)(equalTo(value))
      },
      testM("shorts") {
        val value = 5.toShort
        for {
          ed  <- encodeAndDecode(Schema[Short], value)
          ed2 <- encodeAndDecodeNS(Schema[Short], value)
        } yield assert(ed)(equalTo(Chunk(value))) && assert(ed2)(equalTo(value))
      },
      testM("longs") {
        val value = 1000L
        for {
          ed  <- encodeAndDecode(Schema[Long], value)
          ed2 <- encodeAndDecodeNS(Schema[Long], value)
        } yield assert(ed)(equalTo(Chunk(value))) && assert(ed2)(equalTo(value))
      },
      testM("floats") {
        val value = 0.001f
        for {
          ed  <- encodeAndDecode(Schema[Float], value)
          ed2 <- encodeAndDecodeNS(Schema[Float], value)
        } yield assert(ed)(equalTo(Chunk(value))) && assert(ed2)(equalTo(value))
      },
      testM("doubles") {
        val value = 0.001
        for {
          ed  <- encodeAndDecode(Schema[Double], value)
          ed2 <- encodeAndDecodeNS(Schema[Double], value)
        } yield assert(ed)(equalTo(Chunk(value))) && assert(ed2)(equalTo(value))
      },
      testM("bytes") {
        val value = Chunk.fromArray("some bytes".getBytes)
        for {
          ed  <- encodeAndDecode(Schema[Chunk[Byte]], value)
          ed2 <- encodeAndDecodeNS(Schema[Chunk[Byte]], value)
        } yield assert(ed)(equalTo(Chunk(value))) && assert(ed2)(equalTo(value))
      },
      testM("chars") {
        val value = 'c'
        for {
          ed  <- encodeAndDecode(Schema[Char], value)
          ed2 <- encodeAndDecodeNS(Schema[Char], value)
        } yield assert(ed)(equalTo(Chunk(value))) && assert(ed2)(equalTo(value))
      },
      testM("day of weeks") {
        val value = DayOfWeek.of(3)
        for {
          ed  <- encodeAndDecode(Schema[DayOfWeek], value)
          ed2 <- encodeAndDecodeNS(Schema[DayOfWeek], value)
        } yield assert(ed)(equalTo(Chunk(value))) && assert(ed2)(equalTo(value))
      },
      testM("months") {
        val value = Month.of(3)
        for {
          ed  <- encodeAndDecode(Schema[Month], value)
          ed2 <- encodeAndDecodeNS(Schema[Month], value)
        } yield assert(ed)(equalTo(Chunk(value))) && assert(ed2)(equalTo(value))
      },
      testM("month days") {
        val value = MonthDay.of(1, 31)
        for {
          ed  <- encodeAndDecode(Schema[MonthDay], value)
          ed2 <- encodeAndDecodeNS(Schema[MonthDay], value)
        } yield assert(ed)(equalTo(Chunk(value))) && assert(ed2)(equalTo(value))
      },
      testM("periods") {
        val value = Period.of(5, 3, 1)
        for {
          ed  <- encodeAndDecode(Schema[Period], value)
          ed2 <- encodeAndDecodeNS(Schema[Period], value)
        } yield assert(ed)(equalTo(Chunk(value))) && assert(ed2)(equalTo(value))
      },
      testM("years") {
        val value = Year.of(2020)
        for {
          ed  <- encodeAndDecode(Schema[Year], value)
          ed2 <- encodeAndDecodeNS(Schema[Year], value)
        } yield assert(ed)(equalTo(Chunk(value))) && assert(ed2)(equalTo(value))
      },
      testM("year months") {
        val value = YearMonth.of(2020, 5)
        for {
          ed  <- encodeAndDecode(Schema[YearMonth], value)
          ed2 <- encodeAndDecodeNS(Schema[YearMonth], value)
        } yield assert(ed)(equalTo(Chunk(value))) && assert(ed2)(equalTo(value))
      },
      testM("zone ids") {
        val value = ZoneId.systemDefault()
        for {
          ed  <- encodeAndDecode(Schema[ZoneId], value)
          ed2 <- encodeAndDecodeNS(Schema[ZoneId], value)
        } yield assert(ed)(equalTo(Chunk(value))) && assert(ed2)(equalTo(value))
      },
      testM("zone offsets") {
        val value = ZoneOffset.ofHours(6)
        for {
          ed  <- encodeAndDecode(Schema[ZoneOffset], value)
          ed2 <- encodeAndDecodeNS(Schema[ZoneOffset], value)
        } yield assert(ed)(equalTo(Chunk(value))) && assert(ed2)(equalTo(value))
      },
      testM("durations") {
        val value = Duration.ofDays(12)
        for {
          ed  <- encodeAndDecode(Primitive(StandardType.Duration(ChronoUnit.DAYS)), value)
          ed2 <- encodeAndDecodeNS(Primitive(StandardType.Duration(ChronoUnit.DAYS)), value)
        } yield assert(ed)(equalTo(Chunk(value))) && assert(ed2)(equalTo(value))
      },
      testM("instants") {
        val value = Instant.now()
        for {
          ed  <- encodeAndDecode(Primitive(StandardType.Instant(DateTimeFormatter.ISO_INSTANT)), value)
          ed2 <- encodeAndDecodeNS(Primitive(StandardType.Instant(DateTimeFormatter.ISO_INSTANT)), value)
        } yield assert(ed)(equalTo(Chunk(value))) && assert(ed2)(equalTo(value))
      },
      testM("local dates") {
        val value = LocalDate.now()
        for {
          ed  <- encodeAndDecode(Primitive(StandardType.LocalDate(DateTimeFormatter.ISO_LOCAL_DATE)), value)
          ed2 <- encodeAndDecodeNS(Primitive(StandardType.LocalDate(DateTimeFormatter.ISO_LOCAL_DATE)), value)
        } yield assert(ed)(equalTo(Chunk(value))) && assert(ed2)(equalTo(value))
      },
      testM("local times") {
        val value = LocalTime.now()
        for {
          ed  <- encodeAndDecode(Primitive(StandardType.LocalTime(DateTimeFormatter.ISO_LOCAL_TIME)), value)
          ed2 <- encodeAndDecodeNS(Primitive(StandardType.LocalTime(DateTimeFormatter.ISO_LOCAL_TIME)), value)
        } yield assert(ed)(equalTo(Chunk(value))) && assert(ed2)(equalTo(value))
      },
      testM("local date times") {
        val value = LocalDateTime.now()
        for {
          ed  <- encodeAndDecode(Primitive(StandardType.LocalDateTime(DateTimeFormatter.ISO_LOCAL_DATE_TIME)), value)
          ed2 <- encodeAndDecodeNS(Primitive(StandardType.LocalDateTime(DateTimeFormatter.ISO_LOCAL_DATE_TIME)), value)
        } yield assert(ed)(equalTo(Chunk(value))) && assert(ed2)(equalTo(value))
      },
      testM("offset times") {
        val value = OffsetTime.now()
        for {
          ed  <- encodeAndDecode(Primitive(StandardType.OffsetTime(DateTimeFormatter.ISO_OFFSET_TIME)), value)
          ed2 <- encodeAndDecodeNS(Primitive(StandardType.OffsetTime(DateTimeFormatter.ISO_OFFSET_TIME)), value)
        } yield assert(ed)(equalTo(Chunk(value))) && assert(ed2)(equalTo(value))
      },
      testM("offset date times") {
        val value            = OffsetDateTime.now()
        val offsetDateSchema = Primitive(StandardType.OffsetDateTime(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        for {
          ed  <- encodeAndDecode(offsetDateSchema, value)
          ed2 <- encodeAndDecodeNS(offsetDateSchema, value)
        } yield assert(ed)(equalTo(Chunk(value))) && assert(ed2)(equalTo(value))
      },
      testM("zoned date times") {
        val zoneSchema = Primitive(StandardType.ZonedDateTime(DateTimeFormatter.ISO_ZONED_DATE_TIME))
        val now        = ZonedDateTime.now()
        for {
          ed  <- encodeAndDecode(zoneSchema, now)
          ed2 <- encodeAndDecodeNS(zoneSchema, now)
        } yield assert(ed)(equalTo(Chunk(now))) && assert(ed2)(equalTo(now))
      },
      testM("packed sequences") {
        val list = PackedList(List(3, 270, 86942))
        for {
          ed  <- encodeAndDecode(schemaPackedList, list)
          ed2 <- encodeAndDecodeNS(schemaPackedList, list)
        } yield assert(ed)(equalTo(Chunk(list))) && assert(ed2)(equalTo(list))
      },
      testM("non-packed sequences") {
        val list = UnpackedList(List("foo", "bar", "baz"))
        for {
          ed  <- encodeAndDecode(schemaUnpackedList, list)
          ed2 <- encodeAndDecodeNS(schemaUnpackedList, list)
        } yield assert(ed)(equalTo(Chunk(list))) && assert(ed2)(equalTo(list))
      },
      testM("enumerations") {
        for {
          ed  <- encodeAndDecode(schemaEnumeration, Enumeration(BooleanValue(true)))
          ed2 <- encodeAndDecodeNS(schemaEnumeration, Enumeration(BooleanValue(true)))
        } yield assert(ed)(equalTo(Chunk(Enumeration(BooleanValue(true))))) && assert(ed2)(
          equalTo(Enumeration(BooleanValue(true)))
        )
      },
      testM("tuples") {
        val value = (123, "foo")
        for {
          ed  <- encodeAndDecode(schemaTuple, value)
          ed2 <- encodeAndDecodeNS(schemaTuple, value)
        } yield assert(ed)(equalTo(Chunk(value))) && assert(ed2)(equalTo(value))
      },
      testM("optionals") {
        val value = Some(123)
        for {
          ed  <- encodeAndDecode(Schema.Optional(Schema[Int]), value)
          ed2 <- encodeAndDecodeNS(Schema.Optional(Schema[Int]), value)
        } yield assert(ed)(equalTo(Chunk(value))) && assert(ed2)(equalTo(value))
      }
    ),
    suite("Should successfully decode")(
      testM("incomplete messages using default values") {
        for {
          e  <- decode(schemaRecord, "107B")
          e2 <- decodeNS(schemaRecord, "107B")
        } yield assert(e)(equalTo(Chunk(Record("", 123)))) && assert(e2)(equalTo(Record("", 123)))
      },
      testM("incomplete tuples using default values") {
        for {
          d  <- decode(schemaTuple, "087B")
          d2 <- decodeNS(schemaTuple, "087B")
        } yield assert(d)(equalTo(Chunk((123, "")))) && assert(d2)(equalTo((123, "")))
      },
      testM("empty input") {
        assertM(decode(Schema[Int], ""))(
          equalTo(Chunk.empty)
        )
      },
      testM("empty input by non streaming variant") {
        assertM(decodeNS(Schema[Int], "").run)(
          fails(equalTo("No bytes to decode"))
        )
      }
    ),
    suite("Should fail to decode")(
      testM("unknown wire types") {
        for {
          d  <- decode(schemaRecord, "0F").run
          d2 <- decodeNS(schemaRecord, "0F").run
        } yield assert(d)(fails(equalTo("Failed decoding key: unknown wire type"))) &&
          assert(d2)(fails(equalTo("Failed decoding key: unknown wire type")))
      },
      testM("invalid field numbers") {
        for {
          d  <- decode(schemaRecord, "00").run
          d2 <- decodeNS(schemaRecord, "00").run
        } yield assert(d)(fails(equalTo("Failed decoding key: invalid field number"))) &&
          assert(d2)(fails(equalTo("Failed decoding key: invalid field number")))
      },
      testM("incomplete length delimited values") {
        for {
          d  <- decode(schemaRecord, "0A0346").run
          d2 <- decodeNS(schemaRecord, "0A0346").run
        } yield assert(d)(fails(equalTo("Unexpected end of chunk"))) &&
          assert(d2)(fails(equalTo("Unexpected end of chunk")))
      },
      testM("incomplete var ints") {
        for {
          d  <- decode(schemaRecord, "10FF").run
          d2 <- decodeNS(schemaRecord, "10FF").run
        } yield assert(d)(fails(equalTo("Unexpected end of chunk"))) && assert(d2)(
          fails(equalTo("Unexpected end of chunk"))
        )
      },
      testM("fail schemas") {
        for {
          d  <- decode(schemaFail, "0F").run
          d2 <- decodeNS(schemaFail, "0F").run
        } yield assert(d)(fails(equalTo("failing schema"))) && assert(d2)(fails(equalTo("failing schema")))
      }
    )
  )

  // some tests are based on https://developers.google.com/protocol-buffers/docs/encoding

  case class BasicInt(value: Int)

  val schemaBasicInt: Schema[BasicInt] = Schema.caseClassN(
    "value" -> Schema[Int]
  )(BasicInt, BasicInt.unapply)

  case class BasicString(value: String)

  val schemaBasicString: Schema[BasicString] = Schema.caseClassN(
    "value" -> Schema[String]
  )(BasicString, BasicString.unapply)

  case class BasicFloat(value: Float)

  val schemaBasicFloat: Schema[BasicFloat] = Schema.caseClassN(
    "value" -> Schema[Float]
  )(BasicFloat, BasicFloat.unapply)

  case class BasicDouble(value: Double)

  val schemaBasicDouble: Schema[BasicDouble] = Schema.caseClassN(
    "value" -> Schema[Double]
  )(BasicDouble, BasicDouble.unapply)

  case class Embedded(embedded: BasicInt)

  val schemaEmbedded: Schema[Embedded] = Schema.caseClassN(
    "embedded" -> schemaBasicInt
  )(Embedded, Embedded.unapply)

  case class PackedList(packed: List[Int])

  val schemaPackedList: Schema[PackedList] = Schema.caseClassN(
    "packed" -> Schema.list(Schema[Int])
  )(PackedList, PackedList.unapply)

  case class UnpackedList(items: List[String])

  val schemaUnpackedList: Schema[UnpackedList] = Schema.caseClassN(
    "unpacked" -> Schema.list(Schema[String])
  )(UnpackedList, UnpackedList.unapply)

  case class Record(name: String, value: Int)

  val schemaRecord: Schema[Record] = Schema.caseClassN(
    "name"  -> Schema[String],
    "value" -> Schema[Int]
  )(Record, Record.unapply)

  val schemaTuple: Schema.Tuple[Int, String] = Schema.Tuple(Schema[Int], Schema[String])

  sealed trait OneOf
  case class StringValue(value: String)   extends OneOf
  case class IntValue(value: Int)         extends OneOf
  case class BooleanValue(value: Boolean) extends OneOf

  val schemaOneOf: Schema[OneOf] = Schema.Transform(
    Schema.enumeration(
      Map(
        "string"  -> Schema[String],
        "int"     -> Schema[Int],
        "boolean" -> Schema[Boolean]
      )
    ),
    (value: Map[String, _]) => {
      value
        .get("string")
        .map(v => Right(StringValue(v.asInstanceOf[String])))
        .orElse(value.get("int").map(v => Right(IntValue(v.asInstanceOf[Int]))))
        .orElse(value.get("boolean").map(v => Right(BooleanValue(v.asInstanceOf[Boolean]))))
        .getOrElse(Left("No value found"))
    }, {
      case StringValue(v)  => Right(Map("string"  -> v))
      case IntValue(v)     => Right(Map("int"     -> v))
      case BooleanValue(v) => Right(Map("boolean" -> v))
    }
  )

  case class Enumeration(oneOf: OneOf)

  val schemaEnumeration: Schema[Enumeration] =
    Schema.caseClassN("value" -> schemaOneOf)(Enumeration, Enumeration.unapply)

  val schemaFail: Schema[StringValue] = Schema.fail("failing schema")

  case class SearchRequest(query: String, pageNumber: Int, resultPerPage: Int)

  val schema: Schema[SearchRequest] = Schema.caseClassN(
    "query"         -> Schema[String],
    "pageNumber"    -> Schema[Int],
    "resultPerPage" -> Schema[Int]
  )(SearchRequest, SearchRequest.unapply)

  val message: SearchRequest = SearchRequest("bitcoins", 1, 100)

  def toHex(chunk: Chunk[Byte]): String =
    chunk.toArray.map("%02X".format(_)).mkString

  def fromHex(hex: String): Chunk[Byte] =
    Try(hex.split("(?<=\\G.{2})").map(Integer.parseInt(_, 16).toByte))
      .map(Chunk.fromArray)
      .getOrElse(Chunk.empty)

  def encode[A](schema: Schema[A], input: A): ZIO[Any, Nothing, Chunk[Byte]] =
    ZStream
      .succeed(input)
      .transduce(ProtobufCodec.encoder(schema))
      .run(ZSink.collectAll)

  //NS == non streaming variant of encode
  def encodeNS[A](schema: Schema[A], input: A): ZIO[Any, Nothing, Chunk[Byte]] =
    ZIO.succeed(ProtobufCodec.encode(schema)(input))

  def decode[A](schema: Schema[A], hex: String): ZIO[Any, String, Chunk[A]] =
    ZStream
      .fromChunk(fromHex(hex))
      .transduce(ProtobufCodec.decoder(schema))
      .run(ZSink.collectAll)

  //NS == non streaming variant of decode
  def decodeNS[A](schema: Schema[A], hex: String): ZIO[Any, String, A] =
    ZIO.succeed(ProtobufCodec.decode(schema)(fromHex(hex))).absolve[String, A]

  def encodeAndDecode[A](schema: Schema[A], input: A) =
    ZStream
      .succeed(input)
      .transduce(ProtobufCodec.encoder(schema))
      .transduce(ProtobufCodec.decoder(schema))
      .run(ZSink.collectAll)

  //NS == non streaming variant of encodeAndDecode
  def encodeAndDecodeNS[A](schema: Schema[A], input: A) =
    ZIO
      .succeed(input)
      .map(a => ProtobufCodec.encode(schema)(a))
      .map(ch => ProtobufCodec.decode(schema)(ch))
      .absolve
}
