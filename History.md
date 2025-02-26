0.6.7 / 2025-02-27
==================

* Bump version to improve scanner disabled behaviour somewhat
* Make SOME game functions work with disabled scanner, improve sync
* Make all requests check/consume gameBasket
* Disable scanner if XM runs out
* Make Scanner Disabled scenarios show BIG text
* Make levelling up compatible with 1.101
* Give clearer feedback when dropping items
* Make resolution selector not send initial resolution back to server uselessly
* Fix inventory not displaying if you look at several ops tabs
* Fix new inventory memory leaks
* Make hack results more dismissable than they were before their dismissability was reduced
* Make floaty text appear for recycling and for using cubes
0.6.6 / 2025-02-25
==================

* Bump version for protocol change
* Align portalCoupler latlng expectations with 32bit signed world
0.6.5 / 2025-02-25
==================

* bump version for release
* Improve client/server ap/xm sync and apgain display
* Bump to 0.6.4 for release
0.6.4 / 2025-02-24
==================

* Bump to 0.6.4 for release
* update to match mid 2016 more closely (for now)
* Fix crash that can occur on slightly older androids where switching tasks makes location go wonky
* Fix a crash that can occur on very recent androids where we try to switch fragments too late in
  lifecycle
0.6.3 / 2025-02-05
==================

* Bump version to 0.6.3 to push crash fixes
* WIP vector map support
* Guard against crash in inventory sorts
* Prevent crash during particle slurp
* Make Dunedin not crash the game (64 bit unsigned overflow)
* show scanner disabled quickmessage more reliably
* close all windows if scanner disabled (good idea?)
* Make FragmentInventoryItem finish at appropriate time and not crash so much
* Protect against null hack result bundle crash (race condition)

0.6.2 / 2025-01-30
==================

* Prevent crash when error dialog or similar appears after fragment has gone away
* Post inventory update to View AFTER processing inventory
* Show all 5 OPS tabs, allow AP-less passcodes
* Allow users to redeem passcodes (proviso: inventoryRewards MUST also be sent as inventory)
* Fix crash on resume for the case where nothing's been deleted
* Improve scanner's location (un)availability handling
* Fix several lifecycle NPEs
* Mitigate crash in showing hack results
* Fix click-through bug in fragments
* Update changelog

0.6.1 / 2025-01-26
==================

* Bump version to 0.6.1 to push bugfixes
* Make flipcard buttons work OK again
* Make location inaccurate quickmessage a bit more insistent
* Lines connecting resos to portals should be more visible
* Display floating text when player gains XM
* Make overcharged resonators still draw in inventory list screen
* Improve behaviour of scanner disabled overlay
* Update changelog

0.6.0 / 2025-01-24
==================

* Bump version for releasing recharge functionality
* Improve lifecycle management slightly wrt livedata listeners
* Make recharge button on inventory item screen work
* If updates for your portal just happen to come in while you're looking at it, use them
* Make remote recharging REALLY work and show efficiency
* Small layout improvements around comm and fitting on certain screens
* Let people do very basic recharging (with boosts and local/remote)
* Reduce allocations during map draw and portal info viewing
* (untested) fix Force Sync
* increase contrast on xm particles

0.5.5 / 2025-01-10
==================

* Bump version for deployment
* Don't level up when not showing scanner
* Get hack results in a timely manner
* Make portal image always be a sensible size on portal screen
* Make portal screen load only once on creation
* Make portal image resolution choice stick first time
* Visually eat globs near real time again
* Make basemap switcher reliable
* Make reso damage more visible
* Untested: Make switching basemaps work on demand again
* Make inventory filter prefs work correctly again
* Make mod screen not interfere with scanner operation
* Make deploy screen not interfere with scanner operation
* Make photo rating activity not interfere with scanner functionality
* Make Portal activity not interfere with operation of scanner
* Fix inventory categories not expanding on click in some instances
* Make credits activity not interfere with scanner operation
* Delete unused XM Particle graphic
* Prevent Inventory Item activity from disrupting scanner
* Add history (changelog) file
* Make OPS activity not interfere with scanner operation

0.5.4 / 2025-01-08
==================

* Bump version for website
* bandaid for map not updating when you're in other activities
* optimise draws by removing extraneous handler
* (untested) better batching of map updates
* Prevent 2 rare crashes in force sync
* Make xm graphics simpler and prettier
* Fix map entities not being clickable
* Simplify base map widget class
* fix world not redrawing after changing basemaps
* draw XM particles as polygons with geojson
* Move fields/links/threads to livedata geojson deltas
* More clearly show (in inventory) which portals you own
* Fix player not being able to eat XM properly (again)
* Make links, fields, resothreads all geojson sets
* Allow user to manually check for updates
* early performance improvements in render (hint: NOT ENOUGH)
* Aesthetic improvements and looser protocol requirements
* Improve handling of large XM globs
