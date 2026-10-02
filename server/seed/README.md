# server/seed

Files here are copied into the server dist only if the target file does not already exist, for
data the live server owns afterwards.

- `plugins/LuckPerms/yaml-storage/groups/{default,mod,admin}.yml`: starting permission groups
  (admin inherits mod inherits default, weights 100/50/0). See docs/server.md's first-boot runbook
  for assigning the owner to admin. Once the live server edits these through `/lp`, LuckPerms owns
  them; re-assembling never overwrites a file that already exists on disk.
