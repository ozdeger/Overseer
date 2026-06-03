package plugins.ozdeger.observer.review

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.util.xmlb.annotations.XMap

/** Listeners (e.g. the tool window) are notified when stored reviews change. */
fun interface OverseerReviewListener {
    fun reviewsChanged()
}

/**
 * Project-level store of per-commit reviews. Completed reviews are persisted; in-progress entries
 * are kept in memory only (never persisted, never affect catch-up de-dup). [all] returns the
 * combined list ordered by commit time, newest first.
 */
@Service(Service.Level.PROJECT)
@State(name = "OverseerReviewStore", storages = [Storage("overseer-reviews.xml")])
class ReviewStore(private val project: Project) : PersistentStateComponent<ReviewStore.State> {

    class State {
        @XCollection(style = XCollection.Style.v2)
        var reviews: MutableList<CommitReview> = mutableListOf()

        /** "repoPath@branch" -> last commit sha we've reviewed up to (catch-up high-water mark). */
        @XMap
        var branchTips: MutableMap<String, String> = mutableMapOf()
    }

    private var state = State()

    /** In-progress reviews (key -> entry). In-memory only, never persisted. */
    private val inProgress = LinkedHashMap<String, CommitReview>()

    override fun getState(): State = state
    override fun loadState(s: State) { state = s }

    /** All reviews (in-progress overriding completed by key), ordered by commit time, newest first. */
    @Synchronized
    fun all(): List<CommitReview> {
        val byKey = LinkedHashMap<String, CommitReview>()
        state.reviews.forEach { byKey[it.key] = it }
        inProgress.forEach { (k, v) -> byKey[k] = v }
        return byKey.values.sortedByDescending { it.timestamp }
    }

    /** Completed-review lookup (used for catch-up de-dup); ignores in-progress entries. */
    @Synchronized
    fun find(repoPath: String, commitHash: String): CommitReview? =
        state.reviews.firstOrNull { it.repoPath == repoPath && it.commitHash == commitHash }

    /** Mark a commit as in-progress (renders a spinner row at its commit-time position). */
    @Synchronized
    fun markInProgress(review: CommitReview) {
        review.inProgress = true
        inProgress[review.key] = review
        notifyChanged()
    }

    /** Resolve an in-progress commit to its completed review. */
    @Synchronized
    fun complete(review: CommitReview) {
        review.inProgress = false
        inProgress.remove(review.key)
        putInternal(review)
    }

    /** Drop an in-progress entry without a result (nothing to review / cancelled). */
    @Synchronized
    fun dropInProgress(key: String) {
        if (inProgress.remove(key) != null) notifyChanged()
    }

    /** Insert or replace a completed review. */
    @Synchronized
    fun put(review: CommitReview) = putInternal(review)

    /** Remove a single review (completed and/or in-progress) by key. */
    @Synchronized
    fun removeReview(key: String) {
        val removed = state.reviews.removeAll { it.key == key }
        val wasPending = inProgress.remove(key) != null
        if (removed || wasPending) notifyChanged()
    }

    private fun putInternal(review: CommitReview) {
        state.reviews.removeAll { it.key == review.key }
        state.reviews.add(0, review)
        if (state.reviews.size > MAX_ENTRIES) {
            state.reviews = state.reviews.sortedByDescending { it.timestamp }.take(MAX_ENTRIES).toMutableList()
        }
        notifyChanged()
    }

    @Synchronized
    fun clear() {
        state.reviews.clear()
        inProgress.clear()
        notifyChanged()
    }

    @Synchronized
    fun getBranchTip(key: String): String? = state.branchTips[key]

    @Synchronized
    fun setBranchTip(key: String, sha: String) { state.branchTips[key] = sha }

    private fun notifyChanged() {
        project.messageBus.syncPublisher(TOPIC).reviewsChanged()
    }

    companion object {
        private const val MAX_ENTRIES = 1000
        val TOPIC: Topic<OverseerReviewListener> =
            Topic.create("Overseer reviews changed", OverseerReviewListener::class.java)

        fun getInstance(project: Project): ReviewStore = project.service()
    }
}
