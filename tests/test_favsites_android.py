"""Android Fav Sites / DuckDuckGo: the tile is restored, the row is hidden in Kids mode, no Google stand-in."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CAT = (ROOT / "android/app/src/main/java/com/codeaque/launcher/Catalog.java").read_text()
MA = (ROOT / "android/app/src/main/java/com/codeaque/launcher/MainActivity.java").read_text()
HV = (ROOT / "android/app/src/main/java/com/codeaque/launcher/HomeView.java").read_text()
FS = (ROOT / "android/app/src/main/java/com/codeaque/launcher/FavSites.java").read_text()


def test_duckduckgo_tile_is_on_fav_sites_and_no_longer_opens_google():
    assert 'DUCKDUCKGO = web("DuckDuckGo", "globe", "https://duckduckgo.com/")' in CAT
    assert 'new Category("favsites", "Fav Sites", "globe", DUCKDUCKGO, ADD_FAV)' in CAT
    assert 'Uri.parse("https://duckduckgo.com/")' in MA
    assert 'Uri.parse("https://www.google.com")' not in MA
    assert "class FavSites" in FS


def test_kids_mode_hides_fav_sites_and_blocks_the_actions():
    assert 'new Category("favsites"' not in CAT.split("public static List<Category> kidsMode", 1)[1].split("public static List<Category> adultBrowse", 1)[0]
    assert '"addfav".equals(it.action)' in CAT
    assert 'it.action.startsWith("favopen:")' in CAT
    assert 'duckduckgo.com' in CAT.split("kidsBlocked", 1)[1][:1200]


def test_remove_is_long_press_right_click_and_y():
    assert "askRemoveFav" in HV and "holdFav" in HV and "hitHomeTile" in HV
    assert "KEYCODE_BUTTON_Y" in HV and "Catalog.favSaved(currentItem())" in HV
    assert "isRightClick" in HV and "askRemoveFav" in HV
    assert "void addFavSite()" in MA and "void askRemoveFav" in MA
