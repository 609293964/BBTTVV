package com.bbttvv.app.domain.usecase

import com.bbttvv.app.data.repository.TripleActionRepositoryResult
import com.bbttvv.app.data.repository.UserActionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserInteractionUseCaseTest {

    @Test
    fun `toggle like delegates inverse state to repository`() = runBlocking {
        val repository = FakeUserActionRepository()
        val useCase = UserInteractionUseCase(repository)

        val result = useCase.toggleLike(aid = 42L, isLiked = false)

        assertEquals(42L to true, repository.lastLikeRequest)
        assertEquals(true, result.getOrThrow())
    }

    @Test
    fun `triple action maps repository result to domain result`() = runBlocking {
        val repository = FakeUserActionRepository(
            tripleResult = TripleActionRepositoryResult(
                likeSuccess = true,
                coinSuccess = false,
                coinMessage = "硬币不足",
                favoriteSuccess = true,
                coinsAdded = 0
            )
        )
        val useCase = UserInteractionUseCase(repository)

        val result = useCase.doTripleAction(aid = 7L, currentCoinCount = 1).getOrThrow()

        assertTrue(result.likeSuccess)
        assertFalse(result.coinSuccess)
        assertEquals("硬币不足", result.coinMessage)
        assertTrue(result.favoriteSuccess)
        assertEquals(0, result.coinsAdded)
        assertEquals(7L to 1, repository.lastTripleRequest)
    }

    @Test
    fun `interaction status falls back when individual checks fail`() = runBlocking {
        val repository = FakeUserActionRepository(failFavoriteCheck = true)
        val useCase = UserInteractionUseCase(repository)

        val status = useCase.checkInteractionStatus(aid = 9L, upMid = 10L)

        assertTrue(status.isLiked)
        assertEquals(1, status.coinCount)
        assertFalse(status.isFavorited)
        assertTrue(status.isFollowing)
    }

    private class FakeUserActionRepository(
        private val tripleResult: TripleActionRepositoryResult = TripleActionRepositoryResult(
            likeSuccess = true,
            coinSuccess = true,
            coinMessage = null,
            favoriteSuccess = true,
            coinsAdded = 2
        ),
        private val failFavoriteCheck: Boolean = false
    ) : UserActionRepository {
        var lastLikeRequest: Pair<Long, Boolean>? = null
        var lastTripleRequest: Pair<Long, Int>? = null

        override suspend fun followUser(mid: Long, follow: Boolean): Result<Boolean> {
            return Result.success(follow)
        }

        override suspend fun favoriteVideo(aid: Long, favorite: Boolean): Result<Boolean> {
            return Result.success(favorite)
        }

        override suspend fun likeVideo(aid: Long, like: Boolean): Result<Boolean> {
            lastLikeRequest = aid to like
            return Result.success(like)
        }

        override suspend fun coinVideo(aid: Long, count: Int, alsoLike: Boolean): Result<Boolean> {
            return Result.success(true)
        }

        override suspend fun tripleAction(
            aid: Long,
            currentCoinCount: Int
        ): Result<TripleActionRepositoryResult> {
            lastTripleRequest = aid to currentCoinCount
            return Result.success(tripleResult)
        }

        override suspend fun checkFollowStatus(mid: Long): Boolean = true

        override suspend fun checkFavoriteStatus(aid: Long): Boolean {
            if (failFavoriteCheck) error("favorite check failed")
            return true
        }

        override suspend fun checkLikeStatus(aid: Long): Boolean = true

        override suspend fun checkCoinStatus(aid: Long): Int = 1
    }
}
