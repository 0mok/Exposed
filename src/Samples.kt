package demo

import kotlin.sql.*

object Users : Table() {
    val id = primaryKey("id")
    val name = columnString("name")
    val cityId = columnNullableInt("city_id")

    val city = foreignKey(cityId, Cities)
}

object Cities : Table() {
    val id = primaryKey("id")
    val name = columnString("name")
}

fun main(args: Array<String>) {
    var db = Database("jdbc:h2:mem:test", driver = "org.h2.Driver")

    db.withSession {
        create (Cities, Users)

        insert (Cities.id(1), Cities.name("St. Petersburg"))
        insert (Cities.id(2), Cities.name to "Munich")
        insert (Cities.id(3), Cities.name to "Prague")

        insert (Users.id(1), Users.name("Andrey"), Users.cityId(1))
        insert (Users.id(2), Users.name("Sergey"), Users.cityId(2))
        insert (Users.id(3), Users.name("Eugene"), Users.cityId(2))
        insert (Users.id(4), Users.name("Alex"))
        insert (Users.id(5), Users.name("Something"))

        update (Users.name("Alexey")) where Users.id.equals(4)

        delete(Users) where Users.id.equals(5)

        println("All cities:")

        select (Cities.name) forEach {
            println(it)
        }

        println("Manual join:")

        select (Users.name, Cities.name) where (Users.id.equals(1) or Users.name.equals("Sergey")) and
                Users.id.equals(2) and Users.cityId.equals(Cities.id) forEach {
            val (userName, cityName) = it
            println("$userName lives in $cityName")
        }

        println("Join with foreign key:")

        select (Users.name, Users.cityId, Cities.name) join Users.city where
                Cities.name.equals("St. Petersburg") or Users.cityId.isNull() forEach {
            val (userName, cityId, cityName) = it
            if (cityId != null) {
                println("$userName lives in $cityName")
            } else {
                println("$userName lives nowhere")
            }
        }

        println("Functions and group by:")

        select (Cities.name, Count(Users.id)) join Users groupBy Cities.name forEach {
            val (cityName, userCount) = it
            if (userCount > 0) {
                println("$userCount user(s) live(s) in $cityName")
            } else {
                println("Nobody lives in $cityName")
            }
        }

        drop (Cities, Users)
    }
}