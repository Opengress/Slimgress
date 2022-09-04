# Introduction

First off, thank you for considering contributing to Slimgress. It's people like you that help build great games.
Following these guidelines helps to communicate that you respect the time of the developers managing and developing this open source project.
In return, they should reciprocate that respect in addressing your issue, assessing changes, and helping you finalize your pull requests.

## What to contribute

Keep an open mind!

There are some important things anyone could contribute right now:
- Bug reports (especially crashes, failure to install, things not rendering...)
- Documentation
- Tests
- Art
- Translations

What would be a dream:
- Help with migration from deprecated components to current ones
- Minimisation of unnecessary dependencies
- Activity/UI code (right now I'm writing deploy and recharge UIs, learning as I go...)

## What to not contribute

We don't want:
- Support requests (in the bug/issue tracker) - you can use https://t.me/opengress for that
- ""Bug"" reports where you've noticed that something doesn't LOOK or FEEL like a carbon copy of an earlier game
- Gameplay ideas - we have a reasonably rigid vision, but you can use https://t.me/opengress if you think your idea is the best thing ever
- Server-API-breaking changes
- Any dependencies on proprietary code
- Any more dependencies at all (within reason)

# Ground Rules

* Maintain compatibility with the currently supported range of devices
* Ensure that any code submitted is original work, or (with prior approval) compatible with the project's licenses
* Create issues for any major changes and enhancements that you wish to make. Discuss things transparently and get community feedback.
* Don't add any classes to the codebase unless absolutely needed. Err on the side of using functions.
* Aim to introduce one feature or fix one bug in each commit.
* Be welcoming to newcomers and encourage contributors from all backgrounds.

# Your First Contribution

If you want to help but don't know where to start,
get in touch at https://t.me/opengress and we will tell you what needs work right now,
and we can help you wrangle git and GitHub if you're new to the platform.

# Getting started

1. Create your own fork of the code
2. Do the changes in your fork
3. If you like the change and think the project could use it:
        * Be sure you have followed the code style for the project.
        * Note the Code of Conduct.
        * Send a pull request indicating that you have a CLA on file.

# How to report a bug

You can find support at https://t.me/opengress.

If your bug allows cheating or is a security vulnerability,
consider saying hi in the support chat and asking for a private chat about it.
Disclosing it privately protects you, as a player,
from having the bug you've discovered used against you by other players.

For anything else, the GitHub issue tracker for the project is fine.
Be sure to include, where applicable:
- What type of device you are using
- How to reproduce the bug
- What the expected behaviour in the bug's situation is
- What the actual buggy behaviour (outcome) is

Is a bug a cheat or security problem? Try asking:
- Can I use it to access something that isn't mine?
- Can I use it to break the rules?

If the answer to those sorts of questions is yes,
then you are probably looking at a cheat/security bug. 

# How to suggest a feature or enhancement

For now, you can ask in the support chat (https://t.me/opengress) or file a "bug report" about it.
Include as much detail as reasonably practical.
Why do you think the feature/enhancement would be needed? How would it work? Who would it help?
Do you think it's a big job? Is there any reason why maybe we shouldn't do it?
You might consider including an annotated screenshot, a quick sketch, a link to an example...

Suggestions for aesthetic changes will be considered very low priority, generally, for now.
That is because this project is a race to get a working client to playtest the API, rather than
a mission to create a AAA-class game client.
(A glittering game client may come one day, but right now the priority is to get the game working!
Also, I've got very little experience with ""native Android"" development, so UI work is
very slow with just me on board. At the time of writing this is not a public project,
so maybe this repository's development will go in a different direction in future.)

# Code review process

Currently, the code review process is just that I eyeball things before accepting them.
If there are things to tidy up, or fix, whatever, I'll comment in the pull request.
If the team gets bigger, we will check for any more suitable process.

# Community

Right now, you can chat to us in https://t.me/opengress. There may be other options in future.

# Code, commit message and labeling conventions

## Commit messages
Your commit message should state what you are doing in the commit.
Don't just say "updated README" or "updated ScannerView". What was the update?
Consider instead "Improved build instructions", "fixed typos in README",
"Fixed ScannerView not drawing map on first run on some devices",
"Formatting improvements", "Added fire weapon button" etc.

Commit messages should be one reasonably short line, except for squash commits, where each
squashed message may have its own line. Sometimes you can't avoid a multiline message,
and that's not the end of the world, but one line is better.
Past 120 characters, it might be too long.

Commit messages should be in English,
though I might not enforce that for contributors who can not actually speak English.

## Code style

The Android Studio default is ideal. Your contributions SHOULD be auto-formatted like this.
The main thing is that you are using whitespace and indentation sensibly.
Common sense stuff: 
Don't make dozens of statements on one line, leave a blank line between methods etc.

I don't like ambiguous or misleading code, so please consider putting braces around ALL
if/else paths, even the single-statement ones.

The main thing is: let your IDE format the code for you, and don't sweat too much.

Please add comments, at least to explain any non-obvious code.
Class/method/whatever documentation (docblocks etc) is absent at the moment.
Kudos to you if you include that stuff (you SHOULD),
and you won't make yourself unpopular by improving the situation across the board!
Feel free to improve my comments and documentation.

I'll accept code without tests, but if you can think up tests, they're certainly welcome.
Tests, like comments, are perfectly fine to include in commits on their own!

If your commit fixes a bug or introduces a feature, please try to make sure that it does only *one*
bug/feature. Having said that, minor changes unrelated to the bug/feature are OK. For example,
a feature to allow better sorting of inventory can happily smuggle in typographical, formatting and
logging changes in unrelated files. The goal of having clear changes has to be balanced against
the current desire for rapid development.
