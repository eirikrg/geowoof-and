package com.geowoof.feature.social

import androidx.annotation.StringRes

internal enum class CommunityTab(@param:StringRes val label: Int) {
    Following(R.string.feature_social_tab_following),
    Explore(R.string.feature_social_tab_explore),
}
