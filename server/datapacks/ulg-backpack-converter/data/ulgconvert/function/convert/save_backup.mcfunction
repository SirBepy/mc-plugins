# Invoked as `function ulgconvert:convert/save_backup with storage ulgconvert:temp new` - macro args
# are new's own top-level keys, so $(id) here is new.id, the fresh backpacks.id this conversion just
# claimed. Writing the whole "new" compound (id/tier/owner_uuid/source/converted_at/backup/ejected)
# under a key named after that id ties the backup permanently to the one backpack it restores into -
# see ulgconvert/README.md for the restore path.
$data modify storage ulgconvert:backup backups.$(id) set from storage ulgconvert:temp new
