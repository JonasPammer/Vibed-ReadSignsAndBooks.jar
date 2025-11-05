book /give command generation is wrong
§ dont work. currently:
  give @p written_book[written_book_content={title:"Example Book",author:"PixelTutorials",pages:["§lBold§r\n§oItalic§r\n§nUnderlined§r\n§mStrikethrough§r\n§kGibberish§r\n§0Black§r\n§fWhite§r\n§4Red§r","Symbols:\n❤🔥➜★☠⚠☀☺☹✉☂✘♪♬\n\n|            |<-- SPACES\n"]}]
turns into (when entered):
  give @p written_book[written_book_content={title:"Example Book",author:"PixelTutorials",pages:["lBoldr\noItalicr\nnUnderlinedr\nmStrikethroughr\nkGibberishr\n0Blackr\nfWhiter\n4Redr","Symbols:\n❤🔥➜★☠⚠☀☺☹✉☂✘♪♬\n\n|            |<-- SPACES\n"]}]
it must be (example for 1.12.9, from https://www.gamergeeks.net/apps/minecraft/give-command-generator/written-books):
  give @a written_book[written_book_content={pages:[[[{"text":"Bold","bold":true},"\n",{"text":"Italic","italic":true},"\n",{"text":"Underline","underlined":true},"\n",{"text":"Strikethrough","strikethrough":true},"\n",{"text":"Color","color":"dark_blue"}]]],title:"Book Title",author:"Book Author"}]
, which is probably the way its stored even (so no logic needed from us i'd assume, other then to use the real nbt and not the stendhal extracted stringified versions)

book shulkerbox commands dont work et all, minecraft complains about malformed minecraft:container component: No component with type 'minecraft:pages'. research internet.