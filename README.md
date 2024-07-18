# Slimgress: Opengress API for Android
Slimgress is due for a name change as it has slightly different goals to Norman's upstream Slimgress client.
Norman's client was a client for (at the time) Google's interesting location-based game.
This Slimgress is a dev client for a reimplementation and reimagining of the original game.
A lot of work is needed to get any client up to a playable state, and a lot of work is still ahead
to get the Opengress server software into a playable state.

Only the surface has been scratched, and the problem domain is not yet well understood.

Opengress intends to (initially) implement a client-server network protocol similar to that used
by the old Nemesis client c2015, along with an example game server, associated website, and game client.
Emulating (VERY loosely) Google's game means that it will be very easy for third parties to develop
new clients, now tools, new innovations! Much work had been done to create unofficial clients for
Google's game. See Slimgress, Ingress-iOS, Influx, and a great many more!

Slimgress fits into this picture as an early Android protocol-development (testing) client.
As the server and client codebases improve and the problem domain is better understood,
a stable base should emerge from which we (the hypothetical ""community"") can develop 
a sensible and polished "V2" of the server and a professional quality game client.
Future client and server implementations could be tailored to special use cases.
New game types? New universes? Your campus's special onboarding tour game? YES!
We have a chicken-and-egg problem where we don't have 
a complete client to test with to develop server code, 
and we don't have a server implementation against which to develop client code!
So it all has to develop kind of at the same time, and they're kinda linked together,
and our Slimgress implementation is the proto-game tool we're writing for using to test the server
code.

Knowledge of what the protocol looks like and what things in the protocol mean are discovered from
third-party documentation published online, other third-party client code, screenshots and wikis.

# Implemented in client
- Log in
- View map with portals (tested), links and fields (not tested)
- View basic portal details (picture, title, level)
- Hack portal (incomplete implementation, but it works)
- View inventory (can't do anything with it, but you can view it)
- View and slurp energy globs on map
- Auto update detection
- Deploy and upgrade resonators on portals

# To do soon (almost all of it needing further serverside work)
- Finish polishing hacking
- Drop and recycle
- Fire weapons
- Link portals
- Log out (Workaround: clear data)
- Toggle switches for things like loading portal images and maybe even map tiles (save data)
- Deploy mods to portals

# To do (but it's technical etc)
- Custom player cursor overlay which sizes like a GroundOverlay but rotates to match orientation
- Custom CompassOverlay which might draw onto an external widget and not the MapView, and should lock scanner rotation to IRL orientation when clicked
- Keep scanner's map centred (etc)
- 3D map

# To do eventually (again, needs serverside stuff)
- View and pick up items from map
- Create a variety of game item types
- View players'/own stats/profiles
- Full set of log in and account setup stuff (name changes, training, stale cookie...)
- View scoring information/checkpoints/whatever
- Send/view comms stuff if/as appropriate
- Invite system support??
- More types of logins support?
- Catch-up iOS client etc
- Notifications.... Harder because we are saying NO to fcm/gcm and friends

# Out of scope
- A perfect, 1:1 reimplementation of gameplay logic or protocol of other software
- Animations or any particularly beautiful art etc
- Anything illegal

# Goals
It's hoped that once there's a stable server implementation Slimgress can eventually be retired
in favour of a V2 client written with something performant and cross-platform. Perhaps Qt.
The Slimgress client at this point is imagined as really mostly a client for testing, 
research and development.
I'd hope that eventually there will be a client codebase which can be deployed on Android, iOS,
maybe even Sailfish and future versions of Ubuntu Phone.
To reach all these platforms, we have to eventually let go of Java.
But that's moot right now, when Android development is easy to pick up and has a head start and a
big audience of possible testers.

# Anti goals
Again, let's not tie ourselves down to "Google's game worked like x, so we MUST do x".
They can show us how to make *A* game, but if we want to play *THAT* game,
well there's a new version now, free to play, and if you want to play *THAT*, you *CAN*.
So we're not making that.
But we don't want to reinvent the wheel either,
so let's try to implement something in the same genre using what we can learn from their example.

Another thing we don't want is to ever make a client which:
- takes up all the space on your phone
- eats all your data
- drains your battery rapidly
- makes your phone hot enough to fry eggs
- lags between actions/activities/whatever or has long loading screens
- takes a loooooong time to start/open/resume
- kills your other applications (memory hog etc)

# Roadmap (subject to revision)
## v0.2 - by which time dev client should be ready for alpha/beta testing
- Complete UI for hacking (done)
- Auto update detection (done)
- View and slurp energy globs (done)
- Drop and recycle items
- Lock map to compass

## v0.3 - by which time players should be able to leave marks on the world and play by game rules
- Deploy on to portals to power them up (done)
- Flipcards
- Weapons (firing of)
- Data/battery saver toggles

## v0.4 - by which time it should be possible to have an enemy team or enemy bot
- Portal mods
- Complete handling login/auth situations (need accept TOS, need name change, stale cookie etc)

## v0.5 - by which time strategic gameplay should become a possibility
- Link portals together

## v0.6 - by which time nasty surprises should begin to be known
- Pick up items from map/floor

## v0.7 - Quality of life stuff
- Capsules
- Passcodes
- COMMS type information view/receive/send
- Portal submission (in game client)
- Glyph hacking or similar

## v0.8 - Looking at growth possibilities
- Invites
- More types of logins (google/facebook/ldap/microsoft/github/SMS/email/whatever)

## v0.9 - Things start looking marketable at this moment
- Nemesis (2013) level of playability on android
- Regional and global scoring
- Portal review (in game client)
- Notification support
- Profile pages / badges / whatever

## v1.0 - A stable client emerges, ready for porting, rewrites, overhauls, new content etc
- iOS or final client parity
- Gameplay to remind players of 2016, maybe
