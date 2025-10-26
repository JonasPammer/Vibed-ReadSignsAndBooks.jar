Next to txt, also save as:
* Stendhal Importable
* CSV, like:
X,Y,Z,FoundWhere,Bookname,Author,Pages (one consecutive string)
X,Y,Z,FoundWhere,SignText

Set Authors and Title in Metadata of book file (they are one of the few directly selectable ones in Windows Explorer detail/column view)
There's also "Tags", where we could maybe put the type (ender_chest). if any of this is even possible et all for text files.


Fix Name 
(013_Eisenfarm_by_PixelTutorials_at_player_inventory_player_inventory_pages_1-19.txt -> Eisenfarm_(19)_by_PixelTutorials~player_inventory.txt)
(077_Proposal_by_AlphaBliss_at_-191_65_2632_minecraft_lectern_pages_1-11.txt -> Proposal_(11)_by_AlphaBliss~minecraft_lectern~-191_65_2632_.txt)

fix all_signs.txt: pad each line with (max amount of characters in a mc sign line) spaces to make it a fixed width file and be able to differentiate between lines without a character (which right now does not even exist)

fix all_signs.txt: remove empty signs, only mention in logs


remove "Page 1:" etc. from all_books.txt

figure out non-distracting yet unique seperator for deleniating book pages in both all_books.txt and single book text file