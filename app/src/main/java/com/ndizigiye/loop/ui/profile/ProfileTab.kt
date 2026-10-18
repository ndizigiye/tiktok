package com.ndizigiye.loop.ui.profile

/** The video grids below a profile header. */
enum class ProfileTab {
    VIDEOS,
    LIKED,
    BOOKMARKS;

    companion object {
        /** Bookmarks are private, so only the owner of the profile sees that tab. */
        fun visibleTo(isOwn: Boolean): List<ProfileTab> =
            if (isOwn) ProfileTab.entries else listOf(VIDEOS, LIKED)
    }
}
