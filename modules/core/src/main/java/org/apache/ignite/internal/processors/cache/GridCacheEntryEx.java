/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.cache;

import org.apache.ignite.*;
import org.apache.ignite.cache.eviction.*;
import org.apache.ignite.internal.processors.affinity.*;
import org.apache.ignite.internal.processors.cache.distributed.*;
import org.apache.ignite.internal.processors.cache.transactions.*;
import org.apache.ignite.internal.processors.cache.version.*;
import org.apache.ignite.internal.processors.dr.*;
import org.apache.ignite.internal.util.lang.*;
import org.jetbrains.annotations.*;

import javax.cache.*;
import javax.cache.expiry.*;
import javax.cache.processor.*;
import java.util.*;

/**
 * Internal API for cache entry ({@code 'Ex'} stands for extended).
 */
public interface GridCacheEntryEx {
    /**
     * @return Memory size.
     * @throws IgniteCheckedException If failed.
     */
    public int memorySize() throws IgniteCheckedException;

    /**
     * @return {@code True} if entry is internal cache entry.
     */
    public boolean isInternal();

    /**
     * @return {@code True} if DHT.
     */
    public boolean isDht();

    /**
     * @return {@code True} if near.
     */
    public boolean isNear();

    /**
     * @return {@code True} if replicated.
     */
    public boolean isReplicated();

    /**
     * @return {@code True} if local.
     */
    public boolean isLocal();

    /**
     * @return {@code False} if entry belongs to cache map, {@code true} if this entry was created in colocated
     *      cache and node is not primary for this key.
     */
    public boolean detached();

    /**
     * Note: this method works only for cache configured in ATOMIC mode or for cache that is
     * data center replication target.
     *
     * @return {@code True} if entry has been already deleted.
     */
    public boolean deleted();

    /**
     * @return Context.
     */
    public <K, V> GridCacheContext<K, V> context();

    /**
     * @return Partition ID.
     */
    public int partition();

    /**
     * @return Start version.
     */
    public long startVersion();

    /**
     * @return Key.
     */
    public KeyCacheObject key();

    /**
     * @return Transaction key.
     */
    public IgniteTxKey txKey();

    /**
     * @return Value.
     */
    public CacheObject rawGet();

    /**
     * @param tmp If {@code true} can return temporary instance which is valid while entry lock is held,
     *        temporary object can used for filter evaluation or transform closure execution and
     *        should not be returned to user.
     * @return Value (unmarshalled if needed).
     * @throws IgniteCheckedException If failed.
     */
    public CacheObject rawGetOrUnmarshal(boolean tmp) throws IgniteCheckedException;

    /**
     * @return {@code True} if has value or value bytes.
     */
    public boolean hasValue();

    /**
     * @param val New value.
     * @param ttl Time to live.
     * @return Old value.
     */
    public CacheObject rawPut(CacheObject val, long ttl);

    /**
     * Wraps this map entry into cache entry.
     *
     * @return Wrapped entry.
     */
    public <K, V> Cache.Entry<K, V> wrap();

    /**
     * Wraps entry to an entry with lazy value get.
     *
     * @return Entry.
     */
    public <K, V> Cache.Entry<K, V> wrapLazyValue();

    /**
     * Peeks value provided to public API entries and to entry filters.
     *
     * @return Value.
     */
    @Nullable public CacheObject peekVisibleValue();

    /**
     * @return Entry which is safe to pass into eviction policy.
     */
    public <K, V> EvictableEntry<K, V> wrapEviction();

    /**
     * @return Entry which holds key and version (no value, since entry
     *      is intended to be used in sync evictions checks).
     */
    public <K, V> CacheVersionedEntryImpl<K, V> wrapVersioned();

    /**
     * @return Not-null version if entry is obsolete.
     */
    public GridCacheVersion obsoleteVersion();

    /**
     * @return {@code True} if entry is obsolete.
     */
    public boolean obsolete();

    /**
     * @return {@code True} if entry is obsolete or deleted.
     * @see #deleted()
     */
    public boolean obsoleteOrDeleted();

    /**
     * @param exclude Obsolete version to ignore.
     * @return {@code True} if obsolete version is not {@code null} and is not the
     *      passed in version.
     */
    public boolean obsolete(GridCacheVersion exclude);

    /**
     * @return Entry info.
     */
    @Nullable public GridCacheEntryInfo info();

    /**
     * Invalidates this entry.
     *
     * @param curVer Current version to match ({@code null} means always match).
     * @param newVer New version to set.
     * @return {@code true} if entry is obsolete.
     * @throws IgniteCheckedException If swap could not be released.
     */
    public boolean invalidate(@Nullable GridCacheVersion curVer, GridCacheVersion newVer) throws IgniteCheckedException;

    /**
     * Invalidates this entry if it passes given filter.
     *
     * @param filter Optional filter that entry should pass before invalidation.
     * @return {@code true} if entry was actually invalidated.
     * @throws IgniteCheckedException If swap could not be released.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean invalidate(@Nullable CacheEntryPredicate[] filter)
        throws GridCacheEntryRemovedException, IgniteCheckedException;

    /**
     * @param swap Swap flag.
     * @param obsoleteVer Version for eviction.
     * @param filter Optional filter.
     * @return {@code True} if entry could be evicted.
     * @throws IgniteCheckedException In case of error.
     */
    public boolean evictInternal(boolean swap, GridCacheVersion obsoleteVer,
        @Nullable CacheEntryPredicate[] filter) throws IgniteCheckedException;

    /**
     * Evicts entry when batch evict is performed. When called, does not write entry data to swap, but instead
     * returns batch swap entry if entry was marked obsolete.
     *
     * @param obsoleteVer Version to mark obsolete with.
     * @return Swap entry if this entry was marked obsolete, {@code null} if entry was not evicted.
     * @throws IgniteCheckedException If failed.
     */
    public GridCacheBatchSwapEntry evictInBatchInternal(GridCacheVersion obsoleteVer) throws IgniteCheckedException;

    /**
     * This method should be called each time entry is marked obsolete
     * other than by calling {@link #markObsolete(GridCacheVersion)}.
     */
    public void onMarkedObsolete();

    /**
     * Checks if entry is new assuming lock is held externally.
     *
     * @return {@code True} if entry is new.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean isNew() throws GridCacheEntryRemovedException;

    /**
     * Checks if entry is new while holding lock.
     *
     * @return {@code True} if entry is new.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean isNewLocked() throws GridCacheEntryRemovedException;

    /**
     * @param topVer Topology version where validation should be performed.
     *  When negative the latest available topology should be used.
     *
     * @return Checks if value is valid.
     */
    public boolean valid(AffinityTopologyVersion topVer);

    /**
     * @return {@code True} if partition is in valid.
     */
    public boolean partitionValid();

    /**
     * @param tx Ongoing transaction (possibly null).
     * @param readSwap Flag indicating whether to check swap memory.
     * @param readThrough Flag indicating whether to read through.
     * @param failFast If {@code true}, then throw {@link GridCacheFilterFailedException} if
     *      filter didn't pass.
     * @param unmarshal Unmarshal flag.
     * @param updateMetrics If {@code true} then metrics should be updated.
     * @param evt Flag to signal event notification.
     * @param tmp If {@code true} can return temporary instance which is valid while entry lock is held,
     *        temporary object can used for filter evaluation or transform closure execution and
     *        should not be returned to user.
     * @param subjId Subject ID initiated this read.
     * @param transformClo Transform closure to record event.
     * @param taskName Task name.
     *      together with getting the value is an atomic operation.
     * @param expiryPlc Expiry policy.
     * @return Cached value.
     * @throws IgniteCheckedException If loading value failed.
     * @throws GridCacheEntryRemovedException If entry was removed.
     * @throws GridCacheFilterFailedException If filter failed.
     */
    @Nullable public CacheObject innerGet(@Nullable IgniteInternalTx tx,
        boolean readSwap,
        boolean readThrough,
        boolean failFast,
        boolean unmarshal,
        boolean updateMetrics,
        boolean evt,
        boolean tmp,
        UUID subjId,
        Object transformClo,
        String taskName,
        @Nullable IgniteCacheExpiryPolicy expiryPlc)
        throws IgniteCheckedException, GridCacheEntryRemovedException, GridCacheFilterFailedException;

    /**
     * Reloads entry from underlying storage.
     *
     * @return Reloaded value.
     * @throws IgniteCheckedException If reload failed.
     * @throws GridCacheEntryRemovedException If entry has been removed.
     */
    @Nullable public CacheObject innerReload() throws IgniteCheckedException,
        GridCacheEntryRemovedException;

    /**
     * @param tx Cache transaction.
     * @param evtNodeId ID of node responsible for this change.
     * @param affNodeId Partitioned node iD.
     * @param val Value to set.
     * @param writeThrough If {@code true} then persist to storage.
     * @param retval {@code True} if value should be returned (and unmarshalled if needed).
     * @param ttl Time to live.
     * @param evt Flag to signal event notification.
     * @param metrics Flag to signal metrics update.
     * @param topVer Topology version.
     * @param filter Filter.
     * @param drType DR type.
     * @param drExpireTime DR expire time (if any).
     * @param explicitVer Explicit version (if any).
     * @param subjId Subject ID initiated this update.
     * @param taskName Task name.
     * @return Tuple containing success flag and old value. If success is {@code false},
     *      then value is {@code null}.
     * @throws IgniteCheckedException If storing value failed.
     * @throws GridCacheEntryRemovedException If entry has been removed.
     */
    public GridCacheUpdateTxResult innerSet(
        @Nullable IgniteInternalTx tx,
        UUID evtNodeId,
        UUID affNodeId,
        @Nullable CacheObject val,
        boolean writeThrough,
        boolean retval,
        long ttl,
        boolean evt,
        boolean metrics,
        AffinityTopologyVersion topVer,
        CacheEntryPredicate[] filter,
        GridDrType drType,
        long drExpireTime,
        @Nullable GridCacheVersion explicitVer,
        @Nullable UUID subjId,
        String taskName
    ) throws IgniteCheckedException, GridCacheEntryRemovedException;

    /**
     * @param tx Cache transaction.
     * @param evtNodeId ID of node responsible for this change.
     * @param affNodeId Partitioned node iD.
     * @param writeThrough If {@code true}, persist to the storage.
     * @param retval {@code True} if value should be returned (and unmarshalled if needed).
     * @param evt Flag to signal event notification.
     * @param metrics Flag to signal metrics notification.
     * @param topVer Topology version.
     * @param filter Filter.
     * @param drType DR type.
     * @param explicitVer Explicit version (if any).
     * @param subjId Subject ID initiated this update.
     * @param taskName Task name.
     * @return Tuple containing success flag and old value. If success is {@code false},
     *      then value is {@code null}.
     * @throws IgniteCheckedException If remove failed.
     * @throws GridCacheEntryRemovedException If entry has been removed.
     */
    public GridCacheUpdateTxResult innerRemove(
        @Nullable IgniteInternalTx tx,
        UUID evtNodeId,
        UUID affNodeId,
        boolean writeThrough,
        boolean retval,
        boolean evt,
        boolean metrics,
        AffinityTopologyVersion topVer,
        CacheEntryPredicate[] filter,
        GridDrType drType,
        @Nullable GridCacheVersion explicitVer,
        @Nullable UUID subjId,
        String taskName
    ) throws IgniteCheckedException, GridCacheEntryRemovedException;

    /**
     * @param ver Cache version to set. Entry will be updated only if current version is less then passed version.
     * @param evtNodeId Event node ID.
     * @param affNodeId Affinity node ID.
     * @param op Update operation.
     * @param val Value. Type depends on operation.
     * @param invokeArgs Optional arguments for entry processor.
     * @param writeThrough Write through flag.
     * @param retval Return value flag.
     * @param expiryPlc Expiry policy.
     * @param evt Event flag.
     * @param metrics Metrics update flag.
     * @param primary If update is performed on primary node (the one which assigns version).
     * @param checkVer Whether update should check current version and ignore update if current version is
     *      greater than passed in.
     * @param filter Optional filter to check.
     * @param drType DR type.
     * @param conflictTtl Conflict TTL (if any).
     * @param conflictExpireTime Conflict expire time (if any).
     * @param conflictVer DR version (if any).
     * @param conflictResolve If {@code true} then performs conflicts resolution.
     * @param intercept If {@code true} then calls cache interceptor.
     * @param subjId Subject ID initiated this update.
     * @param taskName Task name.
     * @return Tuple where first value is flag showing whether operation succeeded,
     *      second value is old entry value if return value is requested, third is updated entry value,
     *      fourth is the version to enqueue for deferred delete the fifth is DR conflict context
     *      or {@code null} if conflict resolution was not performed, the last boolean - whether update should be
     *      propagated to backups or not.
     * @throws IgniteCheckedException If update failed.
     * @throws GridCacheEntryRemovedException If entry is obsolete.
     */
    public GridCacheUpdateAtomicResult innerUpdate(
        GridCacheVersion ver,
        UUID evtNodeId,
        UUID affNodeId,
        GridCacheOperation op,
        @Nullable Object val,
        @Nullable Object[] invokeArgs,
        boolean writeThrough,
        boolean retval,
        @Nullable IgniteCacheExpiryPolicy expiryPlc,
        boolean evt,
        boolean metrics,
        boolean primary,
        boolean checkVer,
        AffinityTopologyVersion topVer,
        @Nullable CacheEntryPredicate[] filter,
        GridDrType drType,
        long conflictTtl,
        long conflictExpireTime,
        @Nullable GridCacheVersion conflictVer,
        boolean conflictResolve,
        boolean intercept,
        @Nullable UUID subjId,
        String taskName
    ) throws IgniteCheckedException, GridCacheEntryRemovedException;

    /**
     * Update method for local cache in atomic mode.
     *
     * @param ver Cache version.
     * @param op Operation.
     * @param writeObj Value. Type depends on operation.
     * @param invokeArgs Optional arguments for EntryProcessor.
     * @param writeThrough Write through flag.
     * @param retval Return value flag.
     * @param expiryPlc Expiry policy..
     * @param evt Event flag.
     * @param metrics Metrics update flag.
     * @param filter Optional filter to check.
     * @param intercept If {@code true} then calls cache interceptor.
     * @param subjId Subject ID initiated this update.
     * @param taskName Task name.
     * @return Tuple containing success flag, old value and result for invoke operation.
     * @throws IgniteCheckedException If update failed.
     * @throws GridCacheEntryRemovedException If entry is obsolete.
     */
    public GridTuple3<Boolean, Object, EntryProcessorResult<Object>> innerUpdateLocal(
        GridCacheVersion ver,
        GridCacheOperation op,
        @Nullable Object writeObj,
        @Nullable Object[] invokeArgs,
        boolean writeThrough,
        boolean retval,
        @Nullable ExpiryPolicy expiryPlc,
        boolean evt,
        boolean metrics,
        @Nullable CacheEntryPredicate[] filter,
        boolean intercept,
        @Nullable UUID subjId,
        String taskName
    ) throws IgniteCheckedException, GridCacheEntryRemovedException;

    /**
     * Marks entry as obsolete and, if possible or required, removes it
     * from swap storage.
     *
     * @param ver Obsolete version.
     * @param readers Flag to clear readers as well.
     * @param filter Optional entry filter.
     * @throws IgniteCheckedException If failed to remove from swap.
     * @return {@code True} if entry was not being used, passed the filter and could be removed.
     */
    public boolean clear(GridCacheVersion ver, boolean readers,
        @Nullable CacheEntryPredicate[] filter) throws IgniteCheckedException;

    /**
     * This locks is called by transaction manager during prepare step
     * for optimistic transactions.
     *
     * @param tx Cache transaction.
     * @param timeout Timeout for lock acquisition.
     * @return {@code True} if lock was acquired, {@code false} otherwise.
     * @throws GridCacheEntryRemovedException If this entry is obsolete.
     * @throws GridDistributedLockCancelledException If lock has been cancelled.
     */
    public boolean tmLock(IgniteInternalTx tx, long timeout) throws GridCacheEntryRemovedException,
        GridDistributedLockCancelledException;

    /**
     * Unlocks acquired lock.
     *
     * @param tx Cache transaction.
     * @throws GridCacheEntryRemovedException If this entry has been removed from cache.
     */
    public abstract void txUnlock(IgniteInternalTx tx) throws GridCacheEntryRemovedException;

    /**
     * @param ver Removes lock.
     * @return {@code True} If lock has been removed.
     * @throws GridCacheEntryRemovedException If this entry has been removed from cache.
     */
    public boolean removeLock(GridCacheVersion ver) throws GridCacheEntryRemovedException;

    /**
     * Sets obsolete flag if possible.
     *
     * @param ver Version to set as obsolete.
     * @return {@code True} if entry is obsolete, {@code false} if
     *      entry is still used by other threads or nodes.
     */
    public boolean markObsolete(GridCacheVersion ver);

    /**
     * Sets obsolete flag if entry value is {@code null} or entry is expired and no
     * locks are held.
     *
     * @param ver Version to set as obsolete.
     * @return {@code True} if entry was marked obsolete.
     * @throws IgniteCheckedException If failed.
     */
    public boolean markObsoleteIfEmpty(@Nullable GridCacheVersion ver) throws IgniteCheckedException;

    /**
     * Sets obsolete flag if entry version equals to {@code ver}.
     *
     * @param ver Version to compare with.
     * @return {@code True} if marked obsolete.
     */
    public boolean markObsoleteVersion(GridCacheVersion ver);

    /**
     * @return Version.
     * @throws GridCacheEntryRemovedException If entry has been removed.
     */
    public GridCacheVersion version() throws GridCacheEntryRemovedException;

    /**
     * Peeks into entry without loading value or updating statistics.
     *
     * @param heap Read from heap flag.
     * @param offheap Read from offheap flag.
     * @param swap Read from swap flag.
     * @param topVer Topology version.
     * @param plc Expiry policy if TTL should be updated.
     * @return Value.
     * @throws GridCacheEntryRemovedException If entry has been removed.
     * @throws IgniteCheckedException If failed.
     */
    @Nullable public CacheObject peek(boolean heap,
        boolean offheap,
        boolean swap,
        AffinityTopologyVersion topVer,
        @Nullable IgniteCacheExpiryPolicy plc)
        throws GridCacheEntryRemovedException, IgniteCheckedException;

    /**
     * Peeks into entry without loading value or updating statistics.
     *
     * @param heap Read from heap flag.
     * @param offheap Read from offheap flag.
     * @param swap Read from swap flag.
     * @param plc Expiry policy if TTL should be updated.
     * @return Value.
     * @throws GridCacheEntryRemovedException If entry has been removed.
     * @throws IgniteCheckedException If failed.
     */
    @Nullable public CacheObject peek(boolean heap,
        boolean offheap,
        boolean swap,
        @Nullable IgniteCacheExpiryPolicy plc)
        throws GridCacheEntryRemovedException, IgniteCheckedException;

    /**
     * This method overwrites current in-memory value with new value.
     * <p>
     * Note that this method is non-transactional and non-distributed and should almost
     * never be used. It is meant to be used when fixing some heurisitic error state.
     *
     * @param val Value to set.
     * @return Previous value.
     * @throws IgniteCheckedException If poke operation failed.
     * @throws GridCacheEntryRemovedException if entry was unexpectedly removed.
     */
    public CacheObject poke(CacheObject val) throws GridCacheEntryRemovedException, IgniteCheckedException;

    /**
     * Sets new value if current version is <tt>0</tt>
     *
     * @param val New value.
     * @param ver Version to use.
     * @param ttl Time to live.
     * @param expireTime Expiration time.
     * @param preload Flag indicating whether entry is being preloaded.
     * @param topVer Topology version.
     * @param drType DR type.
     * @return {@code True} if initial value was set.
     * @throws IgniteCheckedException In case of error.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean initialValue(CacheObject val,
        GridCacheVersion ver,
        long ttl,
        long expireTime,
        boolean preload,
        AffinityTopologyVersion topVer,
        GridDrType drType) throws IgniteCheckedException, GridCacheEntryRemovedException;

    /**
     * Sets new value if current version is <tt>0</tt> using swap entry data.
     * Note that this method does not update cache index.
     *
     * @param key Key.
     * @param unswapped Swap entry to set entry state from.
     * @return {@code True} if  initial value was set.
     * @throws IgniteCheckedException In case of error.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean initialValue(KeyCacheObject key, GridCacheSwapEntry unswapped)
        throws IgniteCheckedException, GridCacheEntryRemovedException;

    /**
     * Create versioned entry for this cache entry.
     *
     * @return Versioned entry.
     * @throws IgniteCheckedException In case of error.
     */
    public <K, V> GridCacheVersionedEntryEx<K, V> versionedEntry() throws IgniteCheckedException;

    /**
     * Sets new value if passed in version matches the current version
     * (used for read-through only).
     *
     * @param val New value.
     * @param curVer Version to match or {@code null} if match is not required.
     * @param newVer Version to set.
     * @return {@code True} if versioned matched.
     * @throws IgniteCheckedException If index could not be updated.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean versionedValue(CacheObject val,
        @Nullable GridCacheVersion curVer,
        @Nullable GridCacheVersion newVer)
        throws IgniteCheckedException, GridCacheEntryRemovedException;

    /**
     * Checks if the candidate is either owner or pending.
     *
     * @param ver Candidate version to check.
     * @return {@code True} if the candidate is either owner or pending.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean hasLockCandidate(GridCacheVersion ver) throws GridCacheEntryRemovedException;

    /**
     * Checks if the candidate is either owner or pending.
     *
     * @param threadId ThreadId.
     * @return {@code True} if the candidate is either owner or pending.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean hasLockCandidate(long threadId) throws GridCacheEntryRemovedException;

    /**
     * @param exclude Exclude versions.
     * @return {@code True} if lock is owned by any thread or node.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean lockedByAny(GridCacheVersion... exclude) throws GridCacheEntryRemovedException;

    /**
     * @return {@code True} if lock is owned by current thread.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean lockedByThread() throws GridCacheEntryRemovedException;

    /**
     * @param lockVer Lock ID.
     * @param threadId Thread ID.
     * @return {@code True} if locked either locally or by thread.
     * @throws GridCacheEntryRemovedException If removed.
     */
    public boolean lockedLocallyByIdOrThread(GridCacheVersion lockVer, long threadId)
        throws GridCacheEntryRemovedException;

    /**
     *
     * @param lockVer Lock ID to check.
     * @return {@code True} if lock is owned by candidate.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean lockedLocally(GridCacheVersion lockVer) throws GridCacheEntryRemovedException;

    /**
     * @param threadId Thread ID to check.
     * @param exclude Version to exclude from check.
     * @return {@code True} if lock is owned by given thread.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean lockedByThread(long threadId, GridCacheVersion exclude) throws GridCacheEntryRemovedException;

    /**
     * @param threadId Thread ID to check.
     * @return {@code True} if lock is owned by given thread.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean lockedByThread(long threadId) throws GridCacheEntryRemovedException;

    /**
     * @param ver Version to check for ownership.
     * @return {@code True} if owner has the specified version.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean lockedBy(GridCacheVersion ver) throws GridCacheEntryRemovedException;

    /**
     * Will not fail for removed entries.
     *
     * @param threadId Thread ID to check.
     * @return {@code True} if lock is owned by given thread.
     */
    public boolean lockedByThreadUnsafe(long threadId);

    /**
     * @param ver Version to check for ownership.
     * @return {@code True} if owner has the specified version.
     */
    public boolean lockedByUnsafe(GridCacheVersion ver);

    /**
     *
     * @param lockVer Lock ID to check.
     * @return {@code True} if lock is owned by candidate.
     */
    public boolean lockedLocallyUnsafe(GridCacheVersion lockVer);

    /**
     * @param ver Lock version to check.
     * @return {@code True} if has candidate with given lock ID.
     */
    public boolean hasLockCandidateUnsafe(GridCacheVersion ver);

    /**
     * @param threadId Thread ID.
     * @return Local candidate.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    @Nullable public GridCacheMvccCandidate localCandidate(long threadId) throws GridCacheEntryRemovedException;

    /**
     * Gets all local candidates.
     *
     * @param exclude Versions to exclude from check.
     * @return All local candidates.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public Collection<GridCacheMvccCandidate> localCandidates(@Nullable GridCacheVersion... exclude)
        throws GridCacheEntryRemovedException;

    /**
     * Gets all remote versions.
     *
     * @param exclude Exclude version.
     * @return All remote versions minus the excluded ones, if any.
     */
    public Collection<GridCacheMvccCandidate> remoteMvccSnapshot(GridCacheVersion... exclude);

    /**
     * Gets lock candidate for given lock ID.
     *
     * @param ver Lock version.
     * @return Lock candidate for given ID.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    @Nullable public GridCacheMvccCandidate candidate(GridCacheVersion ver) throws GridCacheEntryRemovedException;

    /**
     * @param nodeId Node ID.
     * @param threadId Thread ID.
     * @return Candidate.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    @Nullable public GridCacheMvccCandidate candidate(UUID nodeId, long threadId)
        throws GridCacheEntryRemovedException;

    /**
     * @return Local owner.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    @Nullable public GridCacheMvccCandidate localOwner() throws GridCacheEntryRemovedException;

    /**
     * @return Value bytes.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public CacheObject valueBytes() throws GridCacheEntryRemovedException;

    /**
     * Gets cached serialized value bytes.
     *
     * @param ver Version for which to get value bytes.
     * @return Serialized value bytes.
     * @throws IgniteCheckedException If serialization failed.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    @Nullable public CacheObject valueBytes(@Nullable GridCacheVersion ver)
        throws IgniteCheckedException, GridCacheEntryRemovedException;

    /**
     * @return Expire time, without accounting for transactions or removals.
     */
    public long rawExpireTime();

    /**
     * @return Expiration time.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public long expireTime() throws GridCacheEntryRemovedException;

    /**
     * @return Expiration time. Does not check for entry obsolete flag.
     */
    public long expireTimeUnlocked();

    /**
     * Callback from ttl processor to cache entry indicating that entry is expired.
     *
     * @param obsoleteVer Version to set obsolete if entry is expired.
     * @return {@code True} if this entry was obsolete or became obsolete as a result of this call.
     */
    public boolean onTtlExpired(GridCacheVersion obsoleteVer);

    /**
     * @return Time to live, without accounting for transactions or removals.
     */
    public long rawTtl();

    /**
     * @return Time to live.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public long ttl() throws GridCacheEntryRemovedException;

    /**
     * @param ver Version.
     * @param ttl Time to live.
     */
    public void updateTtl(@Nullable GridCacheVersion ver, long ttl);

    /**
     * @return Value.
     * @throws IgniteCheckedException If failed to read from swap storage.
     */
    @Nullable public CacheObject unswap() throws IgniteCheckedException;

    /**
     * Unswap ignoring flags.
     *
     * @param needVal If {@code false} then do not need to deserialize value during unswap.
     * @return Value.
     * @throws IgniteCheckedException If failed.
     */
    @Nullable public CacheObject unswap(boolean needVal) throws IgniteCheckedException;

    /**
     * Tests whether or not given metadata is set.
     *
     * @param name Name of the metadata to test.
     * @return Whether or not given metadata is set.
     */
    public boolean hasMeta(UUID name);

    /**
     * Gets metadata by name.
     *
     * @param name Metadata name.
     * @param <V> Type of the value.
     * @return Metadata value or {@code null}.
     */
    @Nullable public <V> V meta(UUID name);

    /**
     * Adds a new metadata.
     *
     * @param name Metadata name.
     * @param val Metadata value.
     * @param <V> Type of the value.
     * @return Metadata previously associated with given name, or
     *      {@code null} if there was none.
     */
    @Nullable public <V> V addMeta(UUID name, V val);

    /**
     * Adds given metadata value only if it was absent.
     *
     * @param name Metadata name.
     * @param val Value to add if it's not attached already.
     * @param <V> Type of the value.
     * @return {@code null} if new value was put, or current value if put didn't happen.
     */
    @Nullable public <V> V putMetaIfAbsent(UUID name, V val);

    /**
     * Replaces given metadata with new {@code newVal} value only if its current value
     * is equal to {@code curVal}. Otherwise, it is no-op.
     *
     * @param name Name of the metadata.
     * @param curVal Current value to check.
     * @param newVal New value.
     * @return {@code true} if replacement occurred, {@code false} otherwise.
     */
    public <V> boolean replaceMeta(UUID name, V curVal, V newVal);

    /**
     * Removes metadata by name.
     *
     * @param name Name of the metadata to remove.
     * @param <V> Type of the value.
     * @return Value of removed metadata or {@code null}.
     */
    @Nullable public <V> V removeMeta(UUID name);

    /**
     * Removes metadata only if its current value is equal to {@code val} passed in.
     *
     * @param name Name of metadata attribute.
     * @param val Value to compare.
     * @param <V> Value type.
     * @return {@code True} if value was removed, {@code false} otherwise.
     */
    public <V> boolean removeMeta(UUID name, V val);
}
