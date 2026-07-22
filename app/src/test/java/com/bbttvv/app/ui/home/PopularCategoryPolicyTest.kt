package com.bbttvv.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class PopularCategoryPolicyTest {
    @Test
    fun `ranking category labels match ranking v2 rid catalog`() {
        assertEquals(
            listOf(
                "热门" to null,
                "动画" to 1005,
                "音乐" to 1003,
                "舞蹈" to 1004,
                "游戏" to 1008,
                "知识" to 1010,
                "科技" to 1012,
                "运动" to 1018,
                "汽车" to 1013,
                "娱乐" to 1002,
                "美食" to 1020,
                "动物圈" to 1024,
                "影视" to 1001,
            ),
            defaultPopularCategories.map { it.label to it.rid },
        )
    }
}
