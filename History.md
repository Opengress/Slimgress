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