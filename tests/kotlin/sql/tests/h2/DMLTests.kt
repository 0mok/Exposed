package kotlin.sql.tests.h2

import kotlin.sql.*
import org.junit.Test
import kotlin.test.assertEquals
import org.joda.time.DateTime
import java.math.BigDecimal
import demo.Cities

object DMLTestsData {
    object Cities : Table() {
        val id = integer("id").autoIncrement().primaryKey() // PKColumn<Int>
        val name = varchar("name", 50) // Column<String>
    }

    object Users : Table() {
        val id = varchar("id", 10).primaryKey() // PKColumn<String>
        val name = varchar("name", length = 50) // Column<String>
        val cityId = (integer("city_id") references Cities.id).nullable() // Column<Int?>
    }

    object UserData : Table() {
        val user_id = varchar("user_id", 10) references Users.id
        val comment = varchar("comment", 30)
        val value = integer("value")
    }

    enum class E {
        ONE
        TWO
        THREE
    }

    object Misc : Table() {
        val n = integer("n")
        val nn = integer("nn").nullable()

        val d = date("d")
        val dn = date("dn").nullable()

        val e = enumeration("e", javaClass<E>())
        val en = enumeration("en", javaClass<E>()).nullable()

        val s = varchar("s", 100)
        val sn = varchar("sn", 100).nullable()

        val dc = decimal("dc", 12, 2)
        val dcn = decimal("dcn", 12, 2).nullable()

    }
}

class DMLTests : DatabaseTestsBase() {
    fun withCitiesAndUsers(statement: Session.(cities: DMLTestsData.Cities, users: DMLTestsData.Users, userData: DMLTestsData.UserData) -> Unit) {
        val Users = DMLTestsData.Users;
        val Cities = DMLTestsData.Cities;
        val UserData = DMLTestsData.UserData;

        withTables(Cities, Users, UserData) {
            val saintPetersburgId = Cities.insert {
                it[name] = "St. Petersburg"
            } get Cities.id

            val munichId = Cities.insert {
                it[name] = "Munich"
            } get Cities.id

            Cities.insert {
                it[name] = "Prague"
            }

            Users.insert {
                it[id] = "andrey"
                it[name] = "Andrey"
                it[cityId] = saintPetersburgId
            }

            Users.insert {
                it[id] = "sergey"
                it[name] = "Sergey"
                it[cityId] = munichId
            }

            Users.insert {
                it[id] = "eugene"
                it[name] = "Eugene"
                it[cityId] = munichId
            }

            Users.insert {
                it[id] = "alex"
                it[name] = "Alex"
                it[cityId] = null
            }

            Users.insert {
                it[id] = "smth"
                it[name] = "Something"
                it[cityId] = null
            }

            UserData.insert {
                it[user_id] = "smth"
                it[comment] = "Something is here"
                it[value] = 10
            }

            UserData.insert {
                it[user_id] = "eugene"
                it[comment] = "Comment for Eugene"
                it[value] = 20
            }

            UserData.insert {
                it[user_id] = "sergey"
                it[comment] = "Comment for Sergey"
                it[value] = 30
            }
            statement (Cities, Users, UserData)
        }
    }

    Test fun testUpdate01() {
        withCitiesAndUsers { cities, users, userData ->
            val alexId = "alex"
            val alexName = users.slice(users.name).select (users.id.eq(alexId)).first()[users.name]
            assertEquals("Alex", alexName);

            val newName = "Alexey"
            users.update(users.id.eq(alexId)) {
                it[users.name] = newName
            }

            val alexNewName = users.slice(users.name).select (users.id.eq(alexId)).first()[users.name]
            assertEquals(newName, alexNewName);
        }
    }

    Test fun testPreparedStatement() {
        withCitiesAndUsers { cities, users, userData ->
            val name = users.select(users.id eq "eugene").first()[users.name]
            assertEquals("Eugene", name)
        }
    }

    Test fun testDelete01() {
        withCitiesAndUsers { cities, users, userData ->
            delete(userData).all()
            val userDataExists = userData.selectAll().any()
            assertEquals(false, userDataExists)

            val smthId = users.slice(users.id).select(users.name.like("%thing")).single()[users.id]
            assertEquals ("smth", smthId)

            delete (users) where users.name.like("%thing")
            val hasSmth = users.slice(users.id).select(users.name.like("%thing")).any()
            assertEquals(false, hasSmth)
        }
    }

    // manual join
    Test fun testJoin01() {
        withCitiesAndUsers { cities, users, userData ->
            (users join cities).slice(users.name, cities.name).
            select((users.id.eq("andrey") or users.name.eq("Sergey")) and users.cityId.eq(cities.id)) forEach {
                val userName = it[users.name]
                val cityName = it[cities.name]
                when (userName) {
                    "Andrey" -> assertEquals("St. Petersburg", cityName)
                    "Sergey" -> assertEquals("Munich", cityName)
                    else -> error ("Unexpected user $userName")
                }
            }
        }
    }

    // join with foreign key
    Test fun testJoin02() {
        withCitiesAndUsers { cities, users, userData ->
            val stPetersburgUser = (users innerJoin cities).slice(users.name, users.cityId, cities.name).
            select(cities.name.eq("St. Petersburg") or users.cityId.isNull()).single()
            assertEquals("Andrey", stPetersburgUser[users.name])
            assertEquals("St. Petersburg", stPetersburgUser[cities.name])
        }
    }

    // triple join
    Test fun testJoin03() {
        withCitiesAndUsers { cities, users, userData ->
            val r = (cities innerJoin users innerJoin userData).selectAll().orderBy(users.id).toList()
            assertEquals (2, r.size)
            assertEquals("Eugene", r[0][users.name])
            assertEquals("Comment for Eugene", r[0][userData.comment])
            assertEquals("Munich", r[0][cities.name])
            assertEquals("Sergey", r[1][users.name])
            assertEquals("Comment for Sergey", r[1][userData.comment])
            assertEquals("Munich", r[1][cities.name])
        }
    }

    // triple join
    Test fun testJoin04() {
        object Numbers : Table() {
            val id = integer("id").primaryKey()
        }

        object Names : Table() {
            val name = varchar("name", 10).primaryKey()
        }

        object Map: Table () {
            val id_ref = integer("id_ref") references Numbers.id
            val name_ref = varchar("name_ref", 10) references Names.name
        }

        withTables (Numbers, Names, Map) {
            Numbers.insert { it[id] = 1 }
            Numbers.insert { it[id] = 2 }
            Names.insert { it[name] = "Foo"}
            Names.insert { it[name] = "Bar"}
            Map.insert {
                it[id_ref] = 2
                it[name_ref] = "Foo"
            }

            val r = (Numbers innerJoin Map innerJoin Names).selectAll().toList()
            assertEquals(1, r.size)
            assertEquals(2, r[0][Numbers.id])
            assertEquals("Foo", r[0][Names.name])
        }
    }

    Test fun testGroupBy01() {
        withCitiesAndUsers { cities, users, userData ->
            (cities join users).slice(cities.name, count(users.id)).selectAll() groupBy cities.name forEach {
                val cityName = it[cities.name]
                val userCount = it[count(users.id)]

                when (cityName) {
                    "Munich" -> assertEquals(2, userCount)
                    "Prague" -> assertEquals(0, userCount)
                    "St. Petersburg" -> assertEquals(1, userCount)
                    else -> error ("Unknow city $cityName")
                }
            }
        }
    }

    Test fun testGroupBy02() {
        withCitiesAndUsers { cities, users, userData ->
            val r = (cities join users).slice(cities.name, count(users.id)).selectAll().groupBy(cities.name).having(count(users.id) eq 1).toList()
            assertEquals(1, r.size())
            assertEquals("St. Petersburg", r[0][cities.name])
            val count = r[0][count(users.id)]
            assertEquals(1, count)
        }
    }

    Test fun testGroupBy03() {
        withCitiesAndUsers { cities, users, userData ->
            val r = (cities join users).slice(cities.name, count(users.id), max(cities.id)).selectAll()
                    .groupBy(cities.name)
                    .having(count(users.id) eq max(cities.id))
                    .orderBy(cities.name)
                    .toList()

            assertEquals(2, r.size())
            0.let {
                assertEquals("Munich", r[it][cities.name])
                val count = r[it][count(users.id)]
                assertEquals(2, count)
                val max = r[it][max(cities.id)]
                assertEquals(2, max)
            }
            1.let {
                assertEquals("St. Petersburg", r[it][cities.name])
                val count = r[it][count(users.id)]
                assertEquals(1, count)
                val max = r[it][max(cities.id)]
                assertEquals(1, max)
            }
        }
    }

    Test fun testGroupBy04() {
        withCitiesAndUsers { cities, users, userData ->
            val r = (cities join users).slice(cities.name, count(users.id), max(cities.id)).selectAll()
                    .groupBy(cities.name)
                    .having(count(users.id) lessEq 42)
                    .orderBy(cities.name)
                    .toList()

            assertEquals(2, r.size())
            0.let {
                assertEquals("Munich", r[it][cities.name])
                val count = r[it][count(users.id)]
                assertEquals(2, count)
            }
            1.let {
                assertEquals("St. Petersburg", r[it][cities.name])
                val count = r[it][count(users.id)]
                assertEquals(1, count)
            }
        }
    }

    Test fun orderBy01() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.selectAll().orderBy (users.id).toList()
            assertEquals(5, r.size)
            assertEquals("alex", r[0][users.id])
            assertEquals("andrey", r[1][users.id])
            assertEquals("eugene", r[2][users.id])
            assertEquals("sergey", r[3][users.id])
            assertEquals("smth", r[4][users.id])
        }
    }

    Test fun orderBy02() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.selectAll().orderBy(users.cityId, false).orderBy (users.id).toList()
            assertEquals(5, r.size)
            assertEquals("eugene", r[0][users.id])
            assertEquals("sergey", r[1][users.id])
            assertEquals("andrey", r[2][users.id])
            assertEquals("alex", r[3][users.id])
            assertEquals("smth", r[4][users.id])
        }
    }

    Test fun orderBy03() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.selectAll().orderBy(users.cityId to false, users.id to true).toList()
            assertEquals(5, r.size)
            assertEquals("eugene", r[0][users.id])
            assertEquals("sergey", r[1][users.id])
            assertEquals("andrey", r[2][users.id])
            assertEquals("alex", r[3][users.id])
            assertEquals("smth", r[4][users.id])
        }
    }

    Test fun testOrderBy04() {
        withCitiesAndUsers { cities, users, userData ->
            val r = (cities innerJoin users).slice(cities.name, count(users.id)).selectAll(). groupBy(cities.name).orderBy(cities.name).toList()
            assertEquals(2, r.size)
            assertEquals("Munich", r[0][cities.name])
            assertEquals(2L, r[0][count(users.id)])
            assertEquals("St. Petersburg", r[1][cities.name])
            assertEquals(1L, r[1][count(users.id)])
        }
    }

    Test fun testSizedIterable() {
        withCitiesAndUsers { cities, users, userData ->
            assertEquals( false, cities.selectAll().empty())
            assertEquals( true, cities.select(cities.name eq "Qwertt").empty())
            assertEquals( 0, cities.select(cities.name eq "Qwertt").count())
            assertEquals( 3, cities.selectAll().count())
        }
    }

    Test fun testExists01() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.select(exists(userData.select((userData.user_id eq users.id) and (userData.comment like "%here%")))).toList()
            assertEquals(1, r.size)
            assertEquals("Something", r[0][users.name])
        }
    }

    Test fun testExists02() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.select(exists(userData.select((userData.user_id eq users.id) and ((userData.comment like "%here%") or (userData.comment like "%Sergey")))))
                    .orderBy(users.id).toList()
            assertEquals(2, r.size)
            assertEquals("Sergey", r[0][users.name])
            assertEquals("Something", r[1][users.name])
        }
    }

    Test fun testExists03() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.select(
                        exists(userData.select((userData.user_id eq users.id) and (userData.comment like "%here%")))
                        or exists(userData.select((userData.user_id eq users.id) and (userData.comment like "%Sergey"))))
                    .orderBy(users.id).toList()
            assertEquals(2, r.size)
            assertEquals("Sergey", r[0][users.name])
            assertEquals("Something", r[1][users.name])
        }
    }

    Test fun testCalc01() {
        withCitiesAndUsers { cities, users, userData ->
            val r = cities.slice(sum(cities.id)).selectAll().toList()
            assertEquals(1, r.size)
            assertEquals(6L, r[0][sum(cities.id)])
        }
    }

    Test fun testCalc02() {
        withCitiesAndUsers { cities, users, userData ->
            val sum = Sum(cities.id + userData.value, IntegerColumnType())
            val r = (users innerJoin userData innerJoin cities).slice(users.id, sum)
                    .selectAll().groupBy(users.id).orderBy(users.id).toList()
            assertEquals(2, r.size)
            assertEquals("eugene", r[0][users.id])
            assertEquals(22L, r[0][sum])
            assertEquals("sergey", r[1][users.id])
            assertEquals(32L, r[1][sum])
        }
    }

    Test fun testCalc03() {
        withCitiesAndUsers { cities, users, userData ->
            val sum = Sum(cities.id*100 + userData.value/10, IntegerColumnType())
            val r = (users innerJoin userData innerJoin cities).slice(users.id, sum)
                    .selectAll().groupBy(users.id).orderBy(users.id).toList()
            assertEquals(2, r.size)
            assertEquals("eugene", r[0][users.id])
            assertEquals(202L, r[0][sum])
            assertEquals("sergey", r[1][users.id])
            assertEquals(203L, r[1][sum])
        }
    }

    Test fun testSubsting01() {
        withCitiesAndUsers { cities, users, userData ->
            val substring = substring(users.name, 0, 2)
            val r = (users).slice(users.id, substring)
                    .selectAll().orderBy(users.id).toList()
            assertEquals(5, r.size)
            assertEquals("Al", r[0][substring])
            assertEquals("An", r[1][substring])
            assertEquals("Eu", r[2][substring])
            assertEquals("Se", r[3][substring])
            assertEquals("So", r[4][substring])
        }
    }

    Test fun testInsertSelect01() {
        withCitiesAndUsers { cities, users, userData ->
            val substring = substring(users.name, 0, 2)
            cities.insert((users).slice(substring).selectAll().orderBy(users.id).limit(2))

            val r = cities.slice(cities.name).selectAll().orderBy(cities.id, false).limit(2).toList()
            assertEquals(2, r.size)
            assertEquals("An", r[0][cities.name])
            assertEquals("Al", r[1][cities.name])
        }
    }

    Test fun testInsertSelect02() {
        withCitiesAndUsers { cities, users, userData ->
            userData.insert(userData.slice(userData.user_id, userData.comment, intParam(42)).selectAll())

            val r = userData.select(userData.value eq 42).orderBy(userData.user_id).toList()
            assertEquals(3, r.size)
        }
    }

    Test fun testSelectCase01() {
        withCitiesAndUsers { cities, users, userData ->
            val field = case().When(users.id eq "alex", stringLiteral("11")).Else (stringLiteral("22"))
            val r = (users).slice(users.id, field).selectAll().orderBy(users.id).limit(2).toList()
            assertEquals(2, r.size)
            assertEquals("11", r[0][field])
            assertEquals("alex", r[0][users.id])
            assertEquals("22", r[1][field])
            assertEquals("andrey", r[1][users.id])
        }
    }

    private fun DMLTestsData.Misc.checkRow(row: ResultRow, n: Int, nn: Int?, d: DateTime, dn: DateTime?, e: DMLTestsData.E, en: DMLTestsData.E?, s: String, sn: String?, dc: BigDecimal, dcn: BigDecimal?) {
        assertEquals(row[this.n], n)
        assertEquals(row[this.nn], nn)
        assertEquals(row[this.d], d)
        assertEquals(row[this.dn], dn)
        assertEquals(row[this.e], e)
        assertEquals(row[this.en], en)
        assertEquals(row[this.s], s)
        assertEquals(row[this.sn], sn)
        assertEquals(row[this.dc], dc)
        assertEquals(row[this.dcn], dcn)
    }

    Test fun testInsert01() {
        val t = DMLTestsData.Misc
        withTables(t) {
            t.insert {
                it[n] = 42
                it[d] = today()
                it[e] = DMLTestsData.E.ONE
                it[s] = "test"
                it[dc] = BigDecimal("239.42")
            }

            val row = t.selectAll().single()
            t.checkRow(row, 42, null, today(), null, DMLTestsData.E.ONE, null, "test", null, BigDecimal("239.42"), null)
        }
    }

    Test fun testInsert02() {
        val t = DMLTestsData.Misc
        withTables(t) {
            t.insert {
                it[n] = 42
                it[nn] = null
                it[d] = today()
                it[dn] = null
                it[e] = DMLTestsData.E.ONE
                it[en] = null
                it[s] = "test"
                it[sn] = null
                it[dc] = BigDecimal("239.42")
                it[dcn] = null
            }

            val row = t.selectAll().single()
            t.checkRow(row, 42, null, today(), null, DMLTestsData.E.ONE, null, "test", null, BigDecimal("239.42"), null)
        }
    }
    Test fun testInsert03() {
        val t = DMLTestsData.Misc
        withTables(t) {
            t.insert {
                it[n] = 42
                it[nn] = 42
                it[d] = today()
                it[dn] = today()
                it[e] = DMLTestsData.E.ONE
                it[en] = DMLTestsData.E.ONE
                it[s] = "test"
                it[sn] = "test"
                it[dc] = BigDecimal("239.42")
                it[dcn] = BigDecimal("239.42")
            }

            val row = t.selectAll().single()
            t.checkRow(row, 42, 42, today(), today(), DMLTestsData.E.ONE, DMLTestsData.E.ONE, "test", "test", BigDecimal("239.42"), BigDecimal("239.42"))
        }
    }

    Test fun testInsert04() {
        val stringThatNeedsEscaping = "A'braham Barakhyahu"
        val t = DMLTestsData.Misc
        withTables(t) {
            t.insert {
                it[n] = 42
                it[d] = today()
                it[e] = DMLTestsData.E.ONE
                it[s] = stringThatNeedsEscaping
                it[dc] = BigDecimal("239.42")
            }

            val row = t.selectAll().single()
            t.checkRow(row, 42, null, today(), null, DMLTestsData.E.ONE, null, stringThatNeedsEscaping, null, BigDecimal("239.42"), null)
        }
    }

/*
    Test fun testInsert05() {
        val stringThatNeedsEscaping = "multi\r\nline"
        val t = DMLTestsData.Misc
        withTables(t) {
            t.insert {
                it[n] = 42
                it[d] = today()
                it[e] = DMLTestsData.E.ONE
                it[s] = stringThatNeedsEscaping
            }

            val row = t.selectAll().single()
            t.checkRow(row, 42, null, today(), null, DMLTestsData.E.ONE, null, stringThatNeedsEscaping, null)
        }
    }
*/

    Test fun testSelect01() {
        val t = DMLTestsData.Misc
        withTables(t) {
            val date = today()
            val sTest = "test"
            val dec = BigDecimal("239.42")
            t.insert {
                it[n] = 42
                it[d] = date
                it[e] = DMLTestsData.E.ONE
                it[s] = sTest
                it[dc] = dec
            }

            t.checkRow(t.select(t.n.eq(42)).single(), 42, null, date, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)
            t.checkRow(t.select(t.nn.isNull()).single(), 42, null, date, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)
            t.checkRow(t.select(t.nn.eq(null)).single(), 42, null, date, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)

            t.checkRow(t.select(t.d.eq(date)).single(), 42, null, date, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)
            t.checkRow(t.select(t.dn.isNull()).single(), 42, null, date, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)
            t.checkRow(t.select(t.dn.eq(null)).single(), 42, null, date, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)

            t.checkRow(t.select(t.e.eq(DMLTestsData.E.ONE)).single(), 42, null, date, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)
            t.checkRow(t.select(t.en.isNull()).single(), 42, null, date, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)
            t.checkRow(t.select(t.en.eq(null)).single(), 42, null, date, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)

            t.checkRow(t.select(t.s.eq(sTest)).single(), 42, null, date, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)
            t.checkRow(t.select(t.sn.isNull()).single(), 42, null, date, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)
            t.checkRow(t.select(t.sn.eq(null)).single(), 42, null, date, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)
        }
    }

    Test fun testSelect02() {
        val t = DMLTestsData.Misc
        withTables(t) {
            val date = today()
            val sTest = "test"
            val eOne = DMLTestsData.E.ONE
            val dec = BigDecimal("239.42")
            t.insert {
                it[n] = 42
                it[nn] = 42
                it[d] = date
                it[dn] = date
                it[e] = eOne
                it[en] = eOne
                it[s] = sTest
                it[sn] = sTest
                it[dc] = dec
                it[dcn] = dec

            }

            t.checkRow(t.select(t.nn.eq(42)).single(), 42, 42, date, date, eOne, eOne, sTest, sTest, dec, dec)
            t.checkRow(t.select(t.nn.isNotNull()).single(), 42, 42, date, date, eOne, eOne, sTest, sTest, dec, dec)

            t.checkRow(t.select(t.dn.eq(date)).single(), 42, 42, date, date, eOne, eOne, sTest, sTest, dec, dec)
            t.checkRow(t.select(t.dn.isNotNull()).single(), 42, 42, date, date, eOne, eOne, sTest, sTest, dec, dec)

            t.checkRow(t.select(t.en.eq(eOne)).single(), 42, 42, date, date, eOne, eOne, sTest, sTest, dec, dec)
            t.checkRow(t.select(t.en.isNotNull()).single(), 42, 42, date, date, eOne, eOne, sTest, sTest, dec, dec)

            t.checkRow(t.select(t.sn.eq(sTest)).single(), 42, 42, date, date, eOne, eOne, sTest, sTest, dec, dec)
            t.checkRow(t.select(t.sn.isNotNull()).single(), 42, 42, date, date, eOne, eOne, sTest, sTest, dec, dec)
        }
    }

    Test fun testUpdate02() {
        val t = DMLTestsData.Misc
        withTables(t) {
            val date = today()
            val eOne = DMLTestsData.E.ONE
            val sTest = "test"
            val dec = BigDecimal("239.42")
            t.insert {
                it[n] = 42
                it[nn] = 42
                it[d] = date
                it[dn] = date
                it[e] = eOne
                it[en] = eOne
                it[s] = sTest
                it[sn] = sTest
                it[dc] = dec
                it[dcn] = dec
            }

            t.update(t.n.eq(42)) {
                it[nn] = null
                it[dn] = null
                it[en] = null
                it[sn] = null
                it[dcn] = null
            }

            val row = t.selectAll().single()
            t.checkRow(row, 42, null, date, null, eOne, null, sTest, null, dec, null)
        }
    }
}
