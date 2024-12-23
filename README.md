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
new clients, new tools, new innovations! Much work had been done to create unofficial clients for
Google's game. See Slimgress, iOS-Ingress, Influx, and a great many more!

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
- Agree to terms of service, pick name, pick faction
- View map with portals and resonators, links and fields
- View BASIC portal details (picture, title, level)
- Hack portal (no minigame or powerups right now)
- View inventory (mostly works but needs overhaul)
- View and slurp energy globs on map
- Auto update detection
- Deploy and upgrade resonators on portals
- Drop and recycle including bulk recycle
- Untargeted radial weapons
- Flipcards
- Mods
- Drop and pickup
- Sending plexts (chat messages)
- Mostly sensible main map (actual scanner) rotation/zoom etc. Still has bugs

# To do soon (almost all of it needing further serverside work)

- Update XM and AP in the client more often
- Link portals
- Log out (Workaround: clear data)
- COMM range, more types of plexts, further improvements
- Create a variety of game item types (many types obtainable but some not activatable)

# To do (but it's technical etc)
- Better portal and deploy screens
- Tutorial, factionhint/invites/verification, general onboarding
- More types of logins support?

# To do eventually (again, needs serverside stuff)
- View players'/own stats/profiles (stats are starting to be recorded now)
- View scoring information/checkpoints/whatever
- Catch-up iOS/cross-platform client etc
- Notifications (not in Telegram).... Harder because we are saying NO to fcm/gcm and friends

# Out of scope
- A perfect, 1:1 reimplementation of gameplay logic, UI, UX or protocol of other software
- Animations or any particularly beautiful art etc
- Anything illegal

# Goals
It's hoped that once there's a stable server implementation Slimgress can eventually be retired
in favour of a V2 client written with something performant and cross-platform.
Perhaps Qt, Godot or libGDX (with RoboVM of course).
The Slimgress client at this point is imagined as really mostly a client for testing, 
research and development.
I'd hope that eventually there will be a client codebase which can be deployed on Android, iOS,
maybe even Sailfish and future versions of Ubuntu Phone.
To reach all these platforms, we probably (well, MAYBE) have to eventually let go of Java.
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

- takes up all the space on your phone (if you don't want it to)
- eats all your data (if you don't want it to)
- drains your battery rapidly (if you don't want it to)
- makes your phone hot enough to fry eggs (if you don't want it to)
- lags between actions/activities/whatever or has long loading screens (this is hard to get right!)
- takes a loooooong time to start/open/resume
- kills your other applications (memory hog etc)

# Roadmap (subject to revision)
## v0.2 - by which time dev client should be ready for alpha/beta testing
- Complete UI for hacking (done)
- Auto update detection (done)
- View and slurp energy globs (done)
- Drop and recycle items (done, protocol might change)
- Lock map to compass (done except for pinch zoom)
- Level up and get a field kit (done, protocol might change)

## v0.3 - by which time players should be able to leave marks on the world and play by game rules
- Deploy on to portals to power them up (done)
- Flipcards (done)
- Weapons (firing of) (done)
- Data/battery saver toggles (some exist now)

## v0.4 - by which time it should be possible to have an enemy team or enemy bot

- Portal mods (done)
- Complete handling login/auth situations (TOS, name change, stale cookie etc) (done)

## v0.5 - by which time strategic gameplay should become a possibility
- Link portals together

## v0.6 - by which time nasty surprises should begin to be known

- Pick up items from map/floor (done)

## v0.7 - Quality of life stuff
- Capsules
- Passcodes
- COMMS type information view/receive/send (send works)
- Portal submission (in game client)
- Glyph hacking or similar

## v0.8 - Looking at growth possibilities
- Invites
- More types of logins (google/facebook/ldap/microsoft/github/SMS/email/whatever)
- General parity with iOS-Ingress

## v0.9 - Things start looking marketable at this moment
- Nemesis (2013) level of playability on android
- Regional and global scoring
- Portal review (in game client)
- Notification support (using Telegram for now)
- Profile pages / badges / whatever

## v1.0 - A stable client emerges, ready for porting, rewrites, overhauls, new content etc
- Equivalent of [REDACTED] parity
- Gameplay to remind players of 2016, maybe

# Controversial issue
Platform support: Ideally we go all the way back to API 1.
We are limited mostly by our dependencies - ACRA needs 26, I think. Material might need 21?
Probably if we start a V2 client we will try to keep this one running in the basic sense for a while
at the lowest possible API level, by ripping out ACRA at least.
A V2 will probably be 26(ish) up.
