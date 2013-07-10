Kotlin SQL Library
==================

This is an early prototype for a library to access SQL over JDBC, written for Kotlin language.

```java
object Users : Table() {
    // Boilerplate #1. We cannot guess the column type in runtime :-(
    val id = column<Int>("id", ColumnType.INT)
    val name = column<String>("name", ColumnType.STRING)
}

fun main(args: Array<String>) {
    var db = Database("jdbc:h2:mem:test", driver = "org.h2.Driver")

    db.withSession {
        it.create(Users)

        it.insert(Users.id to 1, Users.name to "Andrey")
        it.insert(Users.id to 2, Users.name to "Sergey")
        // Unsafe code #1. We cannot check if the value is of column's type
        // or all required columns are specified :-(

        it.select (Users.id, Users.name) where
                ((Users.id.equals(1) or Users.name.equals("Sergey")) and Users.id.equals(2)) forEach {
            // Boilerplate # 2. We cannot write Users.id == 1 || Users.name == "Andrey"
            // and we cannot use the precedence of operators :-(
            println("${it[Users.name]}'s id is ${it[Users.id]}") // Unsafe code #2. We cannot check if row has this column
        }
    }
}
```

Outputs:

    SQL: CREATE TABLE Users (id INT, name VARCHAR(50))
    SQL: INSERT INTO Users (id, name) VALUES (1, 'Andrey')
    SQL: INSERT INTO Users (id, name) VALUES (2, 'Sergey')
    SQL: SELECT id, name FROM Users WHERE (id = 1 or name = 'Sergey') and id = 2
    Sergey's id is 2

