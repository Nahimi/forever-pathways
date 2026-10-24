# Publishing Forever Pathways

A first-time walkthrough: GitHub first (it builds the jar and holds your issues), then CurseForge.

## 1. Put the code on GitHub

GitHub keeps your code online, keeps a history of every change, builds the jar for you, and gives
players an **Issues** page to report bugs.

1. Make an account at github.com.
2. Install **GitHub Desktop** (desktop.github.com) and sign in. It does everything below with buttons.
3. In GitHub Desktop: **File, Add local repository**, pick this `forever-pathways` folder. It will say it
   is not a repository yet; click **create a repository** there.
   - Name: `forever-pathways`
   - Licence: none (the `LICENSE` file is already here)
   - Git ignore: none (`.gitignore` is already here)
4. You now have a first **commit** (a saved snapshot). Click **Publish repository**. Untick "Keep this code
   private" if you want people to see the source.
5. Open `gradle.properties` and change `mod_repo_url` to your repository's address, for example
   `https://github.com/YOUR-NAME/forever-pathways`. In GitHub Desktop, type a summary such as
   "Set repository link", click **Commit to main**, then **Push origin**.

From now on, every time you change something: **Commit** (save a snapshot with a note) then **Push**
(send it to GitHub).

## 2. Get the jar

Each push runs the **Build** workflow. On your repository page, open **Actions**, click the newest run,
and download **forever-pathways** under Artifacts. Inside the zip is `foreverpathways-0.23.0.jar`.

If the run fails, click it to read the error and send it over.

Before uploading, test that exact jar in a clean instance: NeoForge 21.1, ProgressiveStages, and
nothing else. Then once more with Patchouli and Legendary Tabs.

## 3. Turn on issues

Issues are on by default for public repositories. The templates in `.github/ISSUE_TEMPLATE` give
players a **Bug report** and a **Suggestion** form. On your repository, **Settings, General, Features**:
make sure Issues is ticked. Click **Watch** at the top of your repository and choose **All activity**
so you get an email for each new issue.

When a release goes out, you can also make a GitHub **Release**: **Releases, Draft a new release**, tag
`v0.23.0`, attach the jar, paste the changelog.

## Before you publish: the tile pack

The map and panel backgrounds and the meander divider are recoloured from a patterned pixel tile pack.
The copy supplied had no licence file. Find where it came from, check its licence allows use in a
published mod, and credit it in the README (there is a marked spot under Credits). If the licence does
not allow it, run `python3 tools/build_sprites.py` without a tile folder after replacing the three files
in `textures/gui/pathways/` with your own.

## 4. CurseForge

1. Sign in at curseforge.com, then open the **Author Console** (authors.curseforge.com).
2. **Create Project**, game Minecraft, class **Mods**.
3. Fill the fields from `docs/CURSEFORGE_PAGE.md`: name, summary, description, categories, licence
   (MIT), avatar (`curseforge_icon.png`), source and issues links.
4. Add two or three real in-game screenshots: the ring, the Grimoire, a completion banner.
5. Upload the jar with the settings in the table in that file. Mark ProgressiveStages as a
   **required** dependency so the launcher installs it automatically.
6. Submit. A moderator reviews new projects by hand. If they ask for changes, edit the project and
   reply; do not create a second project. The most common request is a fuller description.

## 5. Updates

1. Change `mod_version` in `gradle.properties` (0.23.0 to 0.23.1 for fixes, 0.24.0 for features).
   Never reuse a number.
2. Add a section to `CHANGELOG.md`.
3. Commit, push, download the new jar from Actions, test it.
4. On CurseForge: your project, **Files, Upload file**, same settings, paste the changelog section.
