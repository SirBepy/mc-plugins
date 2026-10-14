# cur is a bundle or a non-empty shulker box - Eclipse would eject it right back out on first open
# (per Joe's conversion-decision call), so it never goes into contents.pages; hand it to the player directly instead.
data modify storage ulgconvert:temp new.ejected append from storage ulgconvert:temp cur
function ulgconvert:convert/classify_next
