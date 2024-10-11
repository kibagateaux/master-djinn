Incantation tests covers entire spell in one place
resolver/setup at http level and core game logic/actions at incantations level
so while code separated by concerns (acl/setup vs doing the actual thing) test suite 
|Concept|Spell|DB|
|----|----|---|
|Requests|http|db|
|Tests|process/logic|state/values|
|Concept|when can I do something? What do i need?|what happens when i do it?|



## Writing / debugging
Sometimes fails silently in tests when a constraint isnt met and just returns nil e.g. no uuid supplied to :Avatar the whole query wont run but it wont throw an error either