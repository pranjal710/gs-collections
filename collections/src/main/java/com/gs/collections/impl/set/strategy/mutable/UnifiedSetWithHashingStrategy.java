/*
 * Copyright 2015 Goldman Sachs.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.gs.collections.impl.set.strategy.mutable;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import com.gs.collections.api.LazyIterable;
import com.gs.collections.api.RichIterable;
import com.gs.collections.api.annotation.Beta;
import com.gs.collections.api.block.HashingStrategy;
import com.gs.collections.api.block.function.Function;
import com.gs.collections.api.block.function.Function0;
import com.gs.collections.api.block.function.Function2;
import com.gs.collections.api.block.function.Function3;
import com.gs.collections.api.block.function.primitive.BooleanFunction;
import com.gs.collections.api.block.function.primitive.ByteFunction;
import com.gs.collections.api.block.function.primitive.CharFunction;
import com.gs.collections.api.block.function.primitive.DoubleFunction;
import com.gs.collections.api.block.function.primitive.FloatFunction;
import com.gs.collections.api.block.function.primitive.IntFunction;
import com.gs.collections.api.block.function.primitive.LongFunction;
import com.gs.collections.api.block.function.primitive.ShortFunction;
import com.gs.collections.api.block.predicate.Predicate;
import com.gs.collections.api.block.predicate.Predicate2;
import com.gs.collections.api.block.procedure.Procedure;
import com.gs.collections.api.block.procedure.Procedure2;
import com.gs.collections.api.block.procedure.primitive.ObjectIntProcedure;
import com.gs.collections.api.list.MutableList;
import com.gs.collections.api.map.MutableMap;
import com.gs.collections.api.ordered.OrderedIterable;
import com.gs.collections.api.partition.set.PartitionMutableSet;
import com.gs.collections.api.set.ImmutableSet;
import com.gs.collections.api.set.MutableSet;
import com.gs.collections.api.set.ParallelUnsortedSetIterable;
import com.gs.collections.api.set.Pool;
import com.gs.collections.api.set.SetIterable;
import com.gs.collections.api.set.UnsortedSetIterable;
import com.gs.collections.api.set.primitive.MutableBooleanSet;
import com.gs.collections.api.set.primitive.MutableByteSet;
import com.gs.collections.api.set.primitive.MutableCharSet;
import com.gs.collections.api.set.primitive.MutableDoubleSet;
import com.gs.collections.api.set.primitive.MutableFloatSet;
import com.gs.collections.api.set.primitive.MutableIntSet;
import com.gs.collections.api.set.primitive.MutableLongSet;
import com.gs.collections.api.set.primitive.MutableShortSet;
import com.gs.collections.api.tuple.Pair;
import com.gs.collections.api.tuple.Twin;
import com.gs.collections.impl.AbstractRichIterable;
import com.gs.collections.impl.block.factory.Procedures2;
import com.gs.collections.impl.block.procedure.MutatingAggregationProcedure;
import com.gs.collections.impl.block.procedure.NonMutatingAggregationProcedure;
import com.gs.collections.impl.block.procedure.PartitionPredicate2Procedure;
import com.gs.collections.impl.block.procedure.PartitionProcedure;
import com.gs.collections.impl.block.procedure.SelectInstancesOfProcedure;
import com.gs.collections.impl.factory.HashingStrategySets;
import com.gs.collections.impl.factory.Lists;
import com.gs.collections.impl.lazy.AbstractLazyIterable;
import com.gs.collections.impl.lazy.parallel.AbstractBatch;
import com.gs.collections.impl.lazy.parallel.AbstractParallelIterable;
import com.gs.collections.impl.lazy.parallel.set.AbstractParallelUnsortedSetIterable;
import com.gs.collections.impl.lazy.parallel.set.CollectUnsortedSetBatch;
import com.gs.collections.impl.lazy.parallel.set.RootUnsortedSetBatch;
import com.gs.collections.impl.lazy.parallel.set.SelectUnsortedSetBatch;
import com.gs.collections.impl.lazy.parallel.set.UnsortedSetBatch;
import com.gs.collections.impl.map.mutable.UnifiedMap;
import com.gs.collections.impl.multimap.set.UnifiedSetMultimap;
import com.gs.collections.impl.multimap.set.strategy.UnifiedSetWithHashingStrategyMultimap;
import com.gs.collections.impl.parallel.BatchIterable;
import com.gs.collections.impl.partition.set.strategy.PartitionUnifiedSetWithHashingStrategy;
import com.gs.collections.impl.set.mutable.SynchronizedMutableSet;
import com.gs.collections.impl.set.mutable.UnifiedSet;
import com.gs.collections.impl.set.mutable.UnmodifiableMutableSet;
import com.gs.collections.impl.set.mutable.primitive.BooleanHashSet;
import com.gs.collections.impl.set.mutable.primitive.ByteHashSet;
import com.gs.collections.impl.set.mutable.primitive.CharHashSet;
import com.gs.collections.impl.set.mutable.primitive.DoubleHashSet;
import com.gs.collections.impl.set.mutable.primitive.FloatHashSet;
import com.gs.collections.impl.set.mutable.primitive.IntHashSet;
import com.gs.collections.impl.set.mutable.primitive.LongHashSet;
import com.gs.collections.impl.set.mutable.primitive.ShortHashSet;
import com.gs.collections.impl.tuple.Tuples;
import com.gs.collections.impl.utility.Iterate;
import com.gs.collections.impl.utility.internal.IterableIterate;
import com.gs.collections.impl.utility.internal.MutableCollectionIterate;
import com.gs.collections.impl.utility.internal.SetIterables;
import net.jcip.annotations.NotThreadSafe;

@NotThreadSafe
public class UnifiedSetWithHashingStrategy<T>
        extends AbstractRichIterable<T>
        implements MutableSet<T>, Externalizable, Pool<T>, BatchIterable<T>
{
    protected static final Object NULL_KEY = new Object()
    {
        @Override
        public boolean equals(Object obj)
        {
            throw new RuntimeException("Possible corruption through unsynchronized concurrent modification.");
        }

        @Override
        public int hashCode()
        {
            throw new RuntimeException("Possible corruption through unsynchronized concurrent modification.");
        }

        @Override
        public String toString()
        {
            return "UnifiedSetWithHashingStrategy.NULL_KEY";
        }
    };

    protected static final float DEFAULT_LOAD_FACTOR = 0.75f;

    protected static final int DEFAULT_INITIAL_CAPACITY = 8;

    private static final long serialVersionUID = 1L;

    protected transient Object[] table;

    protected transient int occupied;

    protected float loadFactor = DEFAULT_LOAD_FACTOR;

    protected int maxSize;

    private HashingStrategy<? super T> hashingStrategy;

    /**
     * @deprecated No argument default constructor used for serialization. Instantiating an UnifiedSetWithHashingStrategyMultimap with
     * this constructor will have a null hashingStrategy and throw NullPointerException when used.
     */
    @Deprecated
    public UnifiedSetWithHashingStrategy()
    {
    }

    public UnifiedSetWithHashingStrategy(HashingStrategy<? super T> hashingStrategy)
    {
        if (hashingStrategy == null)
        {
            throw new IllegalArgumentException("Cannot Instantiate UnifiedSetWithHashingStrategy with null HashingStrategy");
        }
        this.hashingStrategy = hashingStrategy;
        this.allocate(DEFAULT_INITIAL_CAPACITY << 1);
    }

    public UnifiedSetWithHashingStrategy(HashingStrategy<? super T> hashingStrategy, int initialCapacity)
    {
        this(hashingStrategy, initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    public UnifiedSetWithHashingStrategy(HashingStrategy<? super T> hashingStrategy, int initialCapacity, float loadFactor)
    {
        if (initialCapacity < 0)
        {
            throw new IllegalArgumentException("initial capacity cannot be less than 0");
        }
        this.hashingStrategy = hashingStrategy;
        this.loadFactor = loadFactor;
        this.init(this.fastCeil(initialCapacity / loadFactor));
    }

    public UnifiedSetWithHashingStrategy(HashingStrategy<? super T> hashingStrategy, Collection<? extends T> collection)
    {
        this(hashingStrategy, Math.max(collection.size(), DEFAULT_INITIAL_CAPACITY), DEFAULT_LOAD_FACTOR);
        this.addAll(collection);
    }

    public UnifiedSetWithHashingStrategy(HashingStrategy<? super T> hashingStrategy, UnifiedSetWithHashingStrategy<T> set)
    {
        this.hashingStrategy = hashingStrategy;
        this.maxSize = set.maxSize;
        this.loadFactor = set.loadFactor;
        this.occupied = set.occupied;
        this.allocateTable(set.table.length);

        for (int i = 0; i < set.table.length; i++)
        {
            Object key = set.table[i];
            if (key instanceof ChainedBucket)
            {
                this.table[i] = ((ChainedBucket) key).copy();
            }
            else if (key != null)
            {
                this.table[i] = key;
            }
        }
    }

    public static <K> UnifiedSetWithHashingStrategy<K> newSet(HashingStrategy<? super K> hashingStrategy)
    {
        return new UnifiedSetWithHashingStrategy<K>(hashingStrategy);
    }

    public static <K> UnifiedSetWithHashingStrategy<K> newSet(UnifiedSetWithHashingStrategy<K> set)
    {
        return new UnifiedSetWithHashingStrategy<K>(set.hashingStrategy, set);
    }

    public static <K> UnifiedSetWithHashingStrategy<K> newSet(HashingStrategy<? super K> hashingStrategy, int size)
    {
        return new UnifiedSetWithHashingStrategy<K>(hashingStrategy, size);
    }

    public static <K> UnifiedSetWithHashingStrategy<K> newSet(HashingStrategy<? super K> hashingStrategy, Iterable<? extends K> source)
    {
        if (source instanceof UnifiedSetWithHashingStrategy<?>)
        {
            return new UnifiedSetWithHashingStrategy<K>(hashingStrategy, (UnifiedSetWithHashingStrategy<K>) source);
        }
        if (source instanceof Collection<?>)
        {
            return new UnifiedSetWithHashingStrategy<K>(hashingStrategy, (Collection<K>) source);
        }
        if (source == null)
        {
            throw new NullPointerException();
        }
        UnifiedSetWithHashingStrategy<K> result = source instanceof RichIterable<?>
                ? UnifiedSetWithHashingStrategy.newSet(hashingStrategy, ((RichIterable<?>) source).size())
                : UnifiedSetWithHashingStrategy.newSet(hashingStrategy);
        Iterate.forEachWith(source, Procedures2.<K>addToCollection(), result);
        return result;
    }

    public static <K> UnifiedSetWithHashingStrategy<K> newSet(HashingStrategy<? super K> hashingStrategy, int size, float loadFactor)
    {
        return new UnifiedSetWithHashingStrategy<K>(hashingStrategy, size, loadFactor);
    }

    public static <K> UnifiedSetWithHashingStrategy<K> newSetWith(HashingStrategy<? super K> hashingStrategy, K... elements)
    {
        return UnifiedSetWithHashingStrategy.newSet(hashingStrategy, elements.length).with(elements);
    }

    public HashingStrategy<? super T> hashingStrategy()
    {
        return this.hashingStrategy;
    }

    private int fastCeil(float v)
    {
        int possibleResult = (int) v;
        if (v - possibleResult > 0.0F)
        {
            possibleResult++;
        }
        return possibleResult;
    }

    protected int init(int initialCapacity)
    {
        int capacity = 1;
        while (capacity < initialCapacity)
        {
            capacity <<= 1;
        }

        return this.allocate(capacity);
    }

    protected int allocate(int capacity)
    {
        this.allocateTable(capacity);
        this.computeMaxSize(capacity);

        return capacity;
    }

    protected void allocateTable(int sizeToAllocate)
    {
        this.table = new Object[sizeToAllocate];
    }

    protected void computeMaxSize(int capacity)
    {
        // need at least one free slot for open addressing
        this.maxSize = Math.min(capacity - 1, (int) (capacity * this.loadFactor));
    }

    protected final int index(T key)
    {
        // This function ensures that hashCodes that differ only by
        // constant multiples at each bit position have a bounded
        // number of collisions (approximately 8 at default load factor).
        int h = this.hashingStrategy.computeHashCode(key);
        h ^= h >>> 20 ^ h >>> 12;
        h ^= h >>> 7 ^ h >>> 4;
        return h & this.table.length - 1;
    }

    public void clear()
    {
        if (this.occupied == 0)
        {
            return;
        }
        this.occupied = 0;
        Object[] set = this.table;

        for (int i = set.length; i-- > 0; )
        {
            set[i] = null;
        }
    }

    public boolean add(T key)
    {
        int index = this.index(key);
        Object cur = this.table[index];
        if (cur == null)
        {
            this.table[index] = UnifiedSetWithHashingStrategy.toSentinelIfNull(key);
            if (++this.occupied > this.maxSize)
            {
                this.rehash();
            }
            return true;
        }
        if (cur instanceof ChainedBucket || !this.nonNullTableObjectEquals(cur, key))
        {
            return this.chainedAdd(key, index);
        }
        return false;
    }

    private boolean chainedAdd(T key, int index)
    {
        if (this.table[index] instanceof ChainedBucket)
        {
            ChainedBucket bucket = (ChainedBucket) this.table[index];
            do
            {
                if (this.nonNullTableObjectEquals(bucket.zero, key))
                {
                    return false;
                }
                if (bucket.one == null)
                {
                    bucket.one = UnifiedSetWithHashingStrategy.toSentinelIfNull(key);
                    if (++this.occupied > this.maxSize)
                    {
                        this.rehash();
                    }
                    return true;
                }
                if (this.nonNullTableObjectEquals(bucket.one, key))
                {
                    return false;
                }
                if (bucket.two == null)
                {
                    bucket.two = UnifiedSetWithHashingStrategy.toSentinelIfNull(key);
                    if (++this.occupied > this.maxSize)
                    {
                        this.rehash();
                    }
                    return true;
                }
                if (this.nonNullTableObjectEquals(bucket.two, key))
                {
                    return false;
                }
                if (bucket.three instanceof ChainedBucket)
                {
                    bucket = (ChainedBucket) bucket.three;
                    continue;
                }
                if (bucket.three == null)
                {
                    bucket.three = UnifiedSetWithHashingStrategy.toSentinelIfNull(key);
                    if (++this.occupied > this.maxSize)
                    {
                        this.rehash();
                    }
                    return true;
                }
                if (this.nonNullTableObjectEquals(bucket.three, key))
                {
                    return false;
                }
                bucket.three = new ChainedBucket(bucket.three, UnifiedSetWithHashingStrategy.toSentinelIfNull(key));
                if (++this.occupied > this.maxSize)
                {
                    this.rehash();
                }
                return true;
            }
            while (true);
        }
        ChainedBucket newBucket = new ChainedBucket(this.table[index], UnifiedSetWithHashingStrategy.toSentinelIfNull(key));
        this.table[index] = newBucket;
        if (++this.occupied > this.maxSize)
        {
            this.rehash();
        }
        return true;
    }

    protected void rehash()
    {
        this.rehash(this.table.length << 1);
    }

    protected void rehash(int newCapacity)
    {
        int oldLength = this.table.length;
        Object[] old = this.table;
        this.allocate(newCapacity);
        this.occupied = 0;

        for (int i = 0; i < oldLength; i++)
        {
            Object oldKey = old[i];
            if (oldKey instanceof ChainedBucket)
            {
                ChainedBucket bucket = (ChainedBucket) oldKey;
                do
                {
                    if (bucket.zero != null)
                    {
                        this.add(this.nonSentinel(bucket.zero));
                    }
                    if (bucket.one == null)
                    {
                        break;
                    }
                    this.add(this.nonSentinel(bucket.one));
                    if (bucket.two == null)
                    {
                        break;
                    }
                    this.add(this.nonSentinel(bucket.two));
                    if (bucket.three != null)
                    {
                        if (bucket.three instanceof ChainedBucket)
                        {
                            bucket = (ChainedBucket) bucket.three;
                            continue;
                        }
                        this.add(this.nonSentinel(bucket.three));
                    }
                    break;
                }
                while (true);
            }
            else if (oldKey != null)
            {
                this.add(this.nonSentinel(oldKey));
            }
        }
    }

    @Override
    public boolean contains(Object key)
    {
        int index = this.index((T) key);
        Object cur = this.table[index];
        if (cur == null)
        {
            return false;
        }
        if (cur instanceof ChainedBucket)
        {
            return this.chainContains((ChainedBucket) cur, (T) key);
        }
        return this.nonNullTableObjectEquals(cur, (T) key);
    }

    private boolean chainContains(ChainedBucket bucket, T key)
    {
        do
        {
            if (this.nonNullTableObjectEquals(bucket.zero, key))
            {
                return true;
            }
            if (bucket.one == null)
            {
                return false;
            }
            if (this.nonNullTableObjectEquals(bucket.one, key))
            {
                return true;
            }
            if (bucket.two == null)
            {
                return false;
            }
            if (this.nonNullTableObjectEquals(bucket.two, key))
            {
                return true;
            }
            if (bucket.three == null)
            {
                return false;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                continue;
            }
            return this.nonNullTableObjectEquals(bucket.three, key);
        }
        while (true);
    }

    public int getBatchCount(int batchSize)
    {
        return Math.max(1, this.table.length / batchSize);
    }

    public void batchForEach(Procedure<? super T> procedure, int sectionIndex, int sectionCount)
    {
        Object[] set = this.table;
        int sectionSize = set.length / sectionCount;
        int start = sectionSize * sectionIndex;
        int end = sectionIndex == sectionCount - 1 ? set.length : start + sectionSize;
        for (int i = start; i < end; i++)
        {
            Object cur = set[i];
            if (cur != null)
            {
                if (cur instanceof ChainedBucket)
                {
                    this.chainedForEach((ChainedBucket) cur, procedure);
                }
                else
                {
                    procedure.value(this.nonSentinel(cur));
                }
            }
        }
    }

    public UnifiedSetWithHashingStrategy<T> tap(Procedure<? super T> procedure)
    {
        this.forEach(procedure);
        return this;
    }

    public void each(Procedure<? super T> procedure)
    {
        for (int i = 0; i < this.table.length; i++)
        {
            Object cur = this.table[i];
            if (cur instanceof ChainedBucket)
            {
                this.chainedForEach((ChainedBucket) cur, procedure);
            }
            else if (cur != null)
            {
                procedure.value(this.nonSentinel(cur));
            }
        }
    }

    private void chainedForEach(ChainedBucket bucket, Procedure<? super T> procedure)
    {
        do
        {
            procedure.value(this.nonSentinel(bucket.zero));
            if (bucket.one == null)
            {
                return;
            }
            procedure.value(this.nonSentinel(bucket.one));
            if (bucket.two == null)
            {
                return;
            }
            procedure.value(this.nonSentinel(bucket.two));
            if (bucket.three == null)
            {
                return;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                continue;
            }
            procedure.value(this.nonSentinel(bucket.three));
            return;
        }
        while (true);
    }

    @Override
    public <P> void forEachWith(Procedure2<? super T, ? super P> procedure, P parameter)
    {
        for (int i = 0; i < this.table.length; i++)
        {
            Object cur = this.table[i];
            if (cur instanceof ChainedBucket)
            {
                this.chainedForEachWith((ChainedBucket) cur, procedure, parameter);
            }
            else if (cur != null)
            {
                procedure.value(this.nonSentinel(cur), parameter);
            }
        }
    }

    private <P> void chainedForEachWith(
            ChainedBucket bucket,
            Procedure2<? super T, ? super P> procedure,
            P parameter)
    {
        do
        {
            procedure.value(this.nonSentinel(bucket.zero), parameter);
            if (bucket.one == null)
            {
                return;
            }
            procedure.value(this.nonSentinel(bucket.one), parameter);
            if (bucket.two == null)
            {
                return;
            }
            procedure.value(this.nonSentinel(bucket.two), parameter);
            if (bucket.three == null)
            {
                return;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                continue;
            }
            procedure.value(this.nonSentinel(bucket.three), parameter);
            return;
        }
        while (true);
    }

    @Override
    public void forEachWithIndex(ObjectIntProcedure<? super T> objectIntProcedure)
    {
        int count = 0;
        for (int i = 0; i < this.table.length; i++)
        {
            Object cur = this.table[i];
            if (cur instanceof ChainedBucket)
            {
                count = this.chainedForEachWithIndex((ChainedBucket) cur, objectIntProcedure, count);
            }
            else if (cur != null)
            {
                objectIntProcedure.value(this.nonSentinel(cur), count++);
            }
        }
    }

    private int chainedForEachWithIndex(ChainedBucket bucket, ObjectIntProcedure<? super T> procedure, int count)
    {
        do
        {
            procedure.value(this.nonSentinel(bucket.zero), count++);
            if (bucket.one == null)
            {
                return count;
            }
            procedure.value(this.nonSentinel(bucket.one), count++);
            if (bucket.two == null)
            {
                return count;
            }
            procedure.value(this.nonSentinel(bucket.two), count++);
            if (bucket.three == null)
            {
                return count;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                continue;
            }
            procedure.value(this.nonSentinel(bucket.three), count++);
            return count;
        }
        while (true);
    }

    public UnifiedSetWithHashingStrategy<T> newEmpty()
    {
        return UnifiedSetWithHashingStrategy.newSet(this.hashingStrategy);
    }

    public T getFirst()
    {
        for (int i = 0; i < this.table.length; i++)
        {
            Object cur = this.table[i];
            if (cur instanceof ChainedBucket)
            {
                return this.nonSentinel(((ChainedBucket) cur).zero);
            }
            if (cur != null)
            {
                return this.nonSentinel(cur);
            }
        }
        return null;
    }

    public T getLast()
    {
        for (int i = this.table.length - 1; i >= 0; i--)
        {
            Object cur = this.table[i];
            if (cur instanceof ChainedBucket)
            {
                return this.getLast((ChainedBucket) cur);
            }
            if (cur != null)
            {
                return this.nonSentinel(cur);
            }
        }
        return null;
    }

    private T getLast(ChainedBucket bucket)
    {
        while (bucket.three instanceof ChainedBucket)
        {
            bucket = (ChainedBucket) bucket.three;
        }

        if (bucket.three != null)
        {
            return this.nonSentinel(bucket.three);
        }
        if (bucket.two != null)
        {
            return this.nonSentinel(bucket.two);
        }
        if (bucket.one != null)
        {
            return this.nonSentinel(bucket.one);
        }
        assert bucket.zero != null;
        return this.nonSentinel(bucket.zero);
    }

    public UnifiedSetWithHashingStrategy<T> select(Predicate<? super T> predicate)
    {
        return this.select(predicate, this.newEmpty());
    }

    public <P> UnifiedSetWithHashingStrategy<T> selectWith(
            Predicate2<? super T, ? super P> predicate,
            P parameter)
    {
        return this.selectWith(predicate, parameter, this.newEmpty());
    }

    public UnifiedSetWithHashingStrategy<T> reject(Predicate<? super T> predicate)
    {
        return this.reject(predicate, this.newEmpty());
    }

    public <P> UnifiedSetWithHashingStrategy<T> rejectWith(
            Predicate2<? super T, ? super P> predicate,
            P parameter)
    {
        return this.rejectWith(predicate, parameter, this.newEmpty());
    }

    public <P> Twin<MutableList<T>> selectAndRejectWith(
            final Predicate2<? super T, ? super P> predicate,
            P parameter)
    {
        final MutableList<T> positiveResult = Lists.mutable.empty();
        final MutableList<T> negativeResult = Lists.mutable.empty();
        this.forEachWith(new Procedure2<T, P>()
        {
            public void value(T each, P parm)
            {
                (predicate.accept(each, parm) ? positiveResult : negativeResult).add(each);
            }
        }, parameter);
        return Tuples.twin(positiveResult, negativeResult);
    }

    public PartitionMutableSet<T> partition(Predicate<? super T> predicate)
    {
        PartitionMutableSet<T> partitionMutableSet = new PartitionUnifiedSetWithHashingStrategy<T>(this.hashingStrategy);
        this.forEach(new PartitionProcedure<T>(predicate, partitionMutableSet));
        return partitionMutableSet;
    }

    public <P> PartitionMutableSet<T> partitionWith(Predicate2<? super T, ? super P> predicate, P parameter)
    {
        PartitionMutableSet<T> partitionMutableSet = new PartitionUnifiedSetWithHashingStrategy<T>(this.hashingStrategy);
        this.forEach(new PartitionPredicate2Procedure<T, P>(predicate, parameter, partitionMutableSet));
        return partitionMutableSet;
    }

    public <S> UnifiedSetWithHashingStrategy<S> selectInstancesOf(Class<S> clazz)
    {
        UnifiedSetWithHashingStrategy<S> result = (UnifiedSetWithHashingStrategy<S>) this.newEmpty();
        this.forEach(new SelectInstancesOfProcedure<S>(clazz, result));
        return result;
    }

    public void removeIf(Predicate<? super T> predicate)
    {
        IterableIterate.removeIf(this, predicate);
    }

    public <P> void removeIfWith(Predicate2<? super T, ? super P> predicate, P parameter)
    {
        IterableIterate.removeIfWith(this, predicate, parameter);
    }

    public <V> UnifiedSet<V> collect(Function<? super T, ? extends V> function)
    {
        return this.collect(function, UnifiedSet.<V>newSet());
    }

    public MutableBooleanSet collectBoolean(BooleanFunction<? super T> booleanFunction)
    {
        return this.collectBoolean(booleanFunction, new BooleanHashSet());
    }

    public MutableByteSet collectByte(ByteFunction<? super T> byteFunction)
    {
        return this.collectByte(byteFunction, new ByteHashSet());
    }

    public MutableCharSet collectChar(CharFunction<? super T> charFunction)
    {
        return this.collectChar(charFunction, new CharHashSet());
    }

    public MutableDoubleSet collectDouble(DoubleFunction<? super T> doubleFunction)
    {
        return this.collectDouble(doubleFunction, new DoubleHashSet());
    }

    public MutableFloatSet collectFloat(FloatFunction<? super T> floatFunction)
    {
        return this.collectFloat(floatFunction, new FloatHashSet());
    }

    public MutableIntSet collectInt(IntFunction<? super T> intFunction)
    {
        return this.collectInt(intFunction, new IntHashSet());
    }

    public MutableLongSet collectLong(LongFunction<? super T> longFunction)
    {
        return this.collectLong(longFunction, new LongHashSet());
    }

    public MutableShortSet collectShort(ShortFunction<? super T> shortFunction)
    {
        return this.collectShort(shortFunction, new ShortHashSet());
    }

    public <V> UnifiedSet<V> flatCollect(Function<? super T, ? extends Iterable<V>> function)
    {
        return this.flatCollect(function, UnifiedSet.<V>newSet());
    }

    public <P, A> UnifiedSet<A> collectWith(Function2<? super T, ? super P, ? extends A> function, P parameter)
    {
        return this.collectWith(function, parameter, UnifiedSet.<A>newSet());
    }

    public <V> UnifiedSet<V> collectIf(
            Predicate<? super T> predicate, Function<? super T, ? extends V> function)
    {
        return this.collectIf(predicate, function, UnifiedSet.<V>newSet());
    }

    @Override
    public T detect(Predicate<? super T> predicate)
    {
        return this.detect(predicate, 0, this.table.length);
    }

    protected T detect(Predicate<? super T> predicate, int start, int end)
    {
        for (int i = start; i < end; i++)
        {
            Object cur = this.table[i];
            if (cur instanceof ChainedBucket)
            {
                Object chainedDetect = this.chainedDetect((ChainedBucket) cur, predicate);
                if (chainedDetect != null)
                {
                    return this.nonSentinel(chainedDetect);
                }
            }
            else if (cur != null)
            {
                T each = this.nonSentinel(cur);
                if (predicate.accept(each))
                {
                    return each;
                }
            }
        }
        return null;
    }

    private Object chainedDetect(ChainedBucket bucket, Predicate<? super T> predicate)
    {
        do
        {
            if (predicate.accept(this.nonSentinel(bucket.zero)))
            {
                return bucket.zero;
            }
            if (bucket.one == null)
            {
                return null;
            }
            if (predicate.accept(this.nonSentinel(bucket.one)))
            {
                return bucket.one;
            }
            if (bucket.two == null)
            {
                return null;
            }
            if (predicate.accept(this.nonSentinel(bucket.two)))
            {
                return bucket.two;
            }
            if (bucket.three == null)
            {
                return null;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                continue;
            }
            if (predicate.accept(this.nonSentinel(bucket.three)))
            {
                return bucket.three;
            }
            return null;
        }
        while (true);
    }

    protected boolean shortCircuit(
            Predicate<? super T> predicate,
            boolean expected,
            boolean onShortCircuit,
            boolean atEnd)
    {
        return this.shortCircuit(predicate, expected, onShortCircuit, atEnd, 0, this.table.length);
    }

    protected boolean shortCircuit(
            Predicate<? super T> predicate,
            boolean expected,
            boolean onShortCircuit,
            boolean atEnd,
            int start,
            int end)
    {
        for (int i = start; i < end; i++)
        {
            Object cur = this.table[i];
            if (cur instanceof ChainedBucket)
            {
                if (this.chainedShortCircuit((ChainedBucket) cur, predicate, expected))
                {
                    return onShortCircuit;
                }
            }
            else if (cur != null)
            {
                T each = this.nonSentinel(cur);
                if (predicate.accept(each) == expected)
                {
                    return onShortCircuit;
                }
            }
        }
        return atEnd;
    }

    private boolean chainedShortCircuit(
            ChainedBucket bucket,
            Predicate<? super T> predicate,
            boolean expected)
    {
        do
        {
            if (predicate.accept(this.nonSentinel(bucket.zero)) == expected)
            {
                return true;
            }
            if (bucket.one == null)
            {
                return false;
            }
            if (predicate.accept(this.nonSentinel(bucket.one)) == expected)
            {
                return true;
            }
            if (bucket.two == null)
            {
                return false;
            }
            if (predicate.accept(this.nonSentinel(bucket.two)) == expected)
            {
                return true;
            }
            if (bucket.three == null)
            {
                return false;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                continue;
            }
            return predicate.accept(this.nonSentinel(bucket.three)) == expected;
        }
        while (true);
    }

    protected <P> boolean shortCircuitWith(
            Predicate2<? super T, ? super P> predicate2,
            P parameter,
            boolean expected,
            boolean onShortCircuit,
            boolean atEnd)
    {
        for (int i = 0; i < this.table.length; i++)
        {
            Object cur = this.table[i];
            if (cur instanceof ChainedBucket)
            {
                if (this.chainedShortCircuitWith((ChainedBucket) cur, predicate2, parameter, expected))
                {
                    return onShortCircuit;
                }
            }
            else if (cur != null)
            {
                T each = this.nonSentinel(cur);
                if (predicate2.accept(each, parameter) == expected)
                {
                    return onShortCircuit;
                }
            }
        }
        return atEnd;
    }

    private <P> boolean chainedShortCircuitWith(
            ChainedBucket bucket,
            Predicate2<? super T, ? super P> predicate,
            P parameter,
            boolean expected)
    {
        do
        {
            if (predicate.accept(this.nonSentinel(bucket.zero), parameter) == expected)
            {
                return true;
            }
            if (bucket.one == null)
            {
                return false;
            }
            if (predicate.accept(this.nonSentinel(bucket.one), parameter) == expected)
            {
                return true;
            }
            if (bucket.two == null)
            {
                return false;
            }
            if (predicate.accept(this.nonSentinel(bucket.two), parameter) == expected)
            {
                return true;
            }
            if (bucket.three == null)
            {
                return false;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                continue;
            }
            return predicate.accept(this.nonSentinel(bucket.three), parameter) == expected;
        }
        while (true);
    }

    @Override
    public boolean anySatisfy(Predicate<? super T> predicate)
    {
        return this.shortCircuit(predicate, true, true, false);
    }

    @Override
    public <P> boolean anySatisfyWith(
            Predicate2<? super T, ? super P> predicate,
            P parameter)
    {
        return this.shortCircuitWith(predicate, parameter, true, true, false);
    }

    @Override
    public boolean allSatisfy(Predicate<? super T> predicate)
    {
        return this.shortCircuit(predicate, false, false, true);
    }

    @Override
    public <P> boolean allSatisfyWith(
            Predicate2<? super T, ? super P> predicate,
            P parameter)
    {
        return this.shortCircuitWith(predicate, parameter, false, false, true);
    }

    @Override
    public boolean noneSatisfy(Predicate<? super T> predicate)
    {
        return this.shortCircuit(predicate, true, false, true);
    }

    @Override
    public <P> boolean noneSatisfyWith(
            Predicate2<? super T, ? super P> predicate,
            P parameter)
    {
        return this.shortCircuitWith(predicate, parameter, true, false, true);
    }

    public <IV, P> IV injectIntoWith(
            IV injectValue,
            final Function3<? super IV, ? super T, ? super P, ? extends IV> function,
            final P parameter)
    {
        return this.injectInto(injectValue, new Function2<IV, T, IV>()
        {
            public IV value(IV argument1, T argument2)
            {
                return function.value(argument1, argument2, parameter);
            }
        });
    }

    /**
     * @deprecated since 3.0. Use {@link #asLazy()}.{@link #select(Predicate)} instead.
     */
    @Deprecated
    public LazyIterable<T> lazySelect(Predicate<? super T> predicate)
    {
        return this.asLazy().select(predicate);
    }

    /**
     * @deprecated since 3.0. Use {@link #asLazy()}.{@link #reject(Predicate)} instead.
     */
    @Deprecated
    public LazyIterable<T> lazyReject(Predicate<? super T> predicate)
    {
        return this.asLazy().reject(predicate);
    }

    /**
     * @deprecated since 3.0. Use {@link #asLazy()}.{@link #collect(Function)} instead.
     */
    @Deprecated
    public <V> LazyIterable<V> lazyCollect(Function<? super T, ? extends V> function)
    {
        return this.asLazy().collect(function);
    }

    public MutableSet<T> asUnmodifiable()
    {
        return UnmodifiableMutableSet.of(this);
    }

    public MutableSet<T> asSynchronized()
    {
        return SynchronizedMutableSet.of(this);
    }

    public ImmutableSet<T> toImmutable()
    {
        return HashingStrategySets.immutable.withAll(this.hashingStrategy, this);
    }

    public UnifiedSetWithHashingStrategy<T> with(T element)
    {
        this.add(element);
        return this;
    }

    public UnifiedSetWithHashingStrategy<T> with(T element1, T element2)
    {
        this.add(element1);
        this.add(element2);
        return this;
    }

    public UnifiedSetWithHashingStrategy<T> with(T element1, T element2, T element3)
    {
        this.add(element1);
        this.add(element2);
        this.add(element3);
        return this;
    }

    public UnifiedSetWithHashingStrategy<T> with(T... elements)
    {
        this.addAll(Arrays.asList(elements));
        return this;
    }

    public UnifiedSetWithHashingStrategy<T> withAll(Iterable<? extends T> iterable)
    {
        this.addAllIterable(iterable);
        return this;
    }

    public UnifiedSetWithHashingStrategy<T> without(T element)
    {
        this.remove(element);
        return this;
    }

    public UnifiedSetWithHashingStrategy<T> withoutAll(Iterable<? extends T> elements)
    {
        this.removeAllIterable(elements);
        return this;
    }

    public boolean addAll(Collection<? extends T> collection)
    {
        return this.addAllIterable(collection);
    }

    public boolean addAllIterable(Iterable<? extends T> iterable)
    {
        if (iterable instanceof UnifiedSetWithHashingStrategy)
        {
            return this.copySet((UnifiedSetWithHashingStrategy<?>) iterable);
        }
        int size = Iterate.sizeOf(iterable);
        this.ensureCapacity(size);
        int oldSize = this.size();
        Iterate.forEachWith(iterable, Procedures2.<T>addToCollection(), this);
        return this.size() != oldSize;
    }

    private void ensureCapacity(int size)
    {
        if (size > this.maxSize)
        {
            size = (int) (size / this.loadFactor) + 1;
            int capacity = Integer.highestOneBit(size);
            if (size != capacity)
            {
                capacity <<= 1;
            }
            this.rehash(capacity);
        }
    }

    protected boolean copySet(UnifiedSetWithHashingStrategy<?> unifiedset)
    {
        //todo: optimize for current size == 0
        boolean changed = false;
        for (int i = 0; i < unifiedset.table.length; i++)
        {
            Object cur = unifiedset.table[i];
            if (cur instanceof ChainedBucket)
            {
                changed |= this.copyChain((ChainedBucket) cur);
            }
            else if (cur != null)
            {
                changed |= this.add(this.nonSentinel(cur));
            }
        }
        return changed;
    }

    private boolean copyChain(ChainedBucket bucket)
    {
        boolean changed = false;
        do
        {
            changed |= this.add(this.nonSentinel(bucket.zero));
            if (bucket.one == null)
            {
                return changed;
            }
            changed |= this.add(this.nonSentinel(bucket.one));
            if (bucket.two == null)
            {
                return changed;
            }
            changed |= this.add(this.nonSentinel(bucket.two));
            if (bucket.three == null)
            {
                return changed;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                continue;
            }
            changed |= this.add(this.nonSentinel(bucket.three));
            return changed;
        }
        while (true);
    }

    public boolean remove(Object key)
    {
        int index = this.index((T) key);

        Object cur = this.table[index];
        if (cur == null)
        {
            return false;
        }
        if (cur instanceof ChainedBucket)
        {
            return this.removeFromChain((ChainedBucket) cur, (T) key, index);
        }
        if (this.nonNullTableObjectEquals(cur, (T) key))
        {
            this.table[index] = null;
            this.occupied--;
            return true;
        }
        return false;
    }

    private boolean removeFromChain(ChainedBucket bucket, T key, int index)
    {
        if (this.nonNullTableObjectEquals(bucket.zero, key))
        {
            bucket.zero = bucket.removeLast(0);
            if (bucket.zero == null)
            {
                this.table[index] = null;
            }
            this.occupied--;
            return true;
        }
        if (bucket.one == null)
        {
            return false;
        }
        if (this.nonNullTableObjectEquals(bucket.one, key))
        {
            bucket.one = bucket.removeLast(1);
            this.occupied--;
            return true;
        }
        if (bucket.two == null)
        {
            return false;
        }
        if (this.nonNullTableObjectEquals(bucket.two, key))
        {
            bucket.two = bucket.removeLast(2);
            this.occupied--;
            return true;
        }
        if (bucket.three == null)
        {
            return false;
        }
        if (bucket.three instanceof ChainedBucket)
        {
            return this.removeDeepChain(bucket, key);
        }
        if (this.nonNullTableObjectEquals(bucket.three, key))
        {
            bucket.three = bucket.removeLast(3);
            this.occupied--;
            return true;
        }
        return false;
    }

    private boolean removeDeepChain(ChainedBucket oldBucket, T key)
    {
        do
        {
            ChainedBucket bucket = (ChainedBucket) oldBucket.three;
            if (this.nonNullTableObjectEquals(bucket.zero, key))
            {
                bucket.zero = bucket.removeLast(0);
                if (bucket.zero == null)
                {
                    oldBucket.three = null;
                }
                this.occupied--;
                return true;
            }
            if (bucket.one == null)
            {
                return false;
            }
            if (this.nonNullTableObjectEquals(bucket.one, key))
            {
                bucket.one = bucket.removeLast(1);
                this.occupied--;
                return true;
            }
            if (bucket.two == null)
            {
                return false;
            }
            if (this.nonNullTableObjectEquals(bucket.two, key))
            {
                bucket.two = bucket.removeLast(2);
                this.occupied--;
                return true;
            }
            if (bucket.three == null)
            {
                return false;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                oldBucket = bucket;
                continue;
            }
            if (this.nonNullTableObjectEquals(bucket.three, key))
            {
                bucket.three = bucket.removeLast(3);
                this.occupied--;
                return true;
            }
            return false;
        }
        while (true);
    }

    public int size()
    {
        return this.occupied;
    }

    @Override
    public boolean equals(Object object)
    {
        if (this == object)
        {
            return true;
        }

        if (!(object instanceof Set))
        {
            return false;
        }

        Set<?> other = (Set<?>) object;
        return this.size() == other.size() && this.containsAll(other);
    }

    @Override
    public int hashCode()
    {
        int hashCode = 0;
        for (int i = 0; i < this.table.length; i++)
        {
            Object cur = this.table[i];
            if (cur instanceof ChainedBucket)
            {
                hashCode += this.chainedHashCode((ChainedBucket) cur);
            }
            else if (cur != null)
            {
                hashCode += this.hashingStrategy.computeHashCode(this.nonSentinel(cur));
            }
        }
        return hashCode;
    }

    private int chainedHashCode(ChainedBucket bucket)
    {
        int hashCode = 0;
        do
        {
            hashCode += this.hashingStrategy.computeHashCode(this.nonSentinel(bucket.zero));
            if (bucket.one == null)
            {
                return hashCode;
            }
            hashCode += this.hashingStrategy.computeHashCode(this.nonSentinel(bucket.one));
            if (bucket.two == null)
            {
                return hashCode;
            }
            hashCode += this.hashingStrategy.computeHashCode(this.nonSentinel(bucket.two));
            if (bucket.three == null)
            {
                return hashCode;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                continue;
            }
            hashCode += this.hashingStrategy.computeHashCode(this.nonSentinel(bucket.three));
            return hashCode;
        }
        while (true);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
    {
        this.hashingStrategy = (HashingStrategy<? super T>) in.readObject();
        int size = in.readInt();
        this.loadFactor = in.readFloat();
        this.init(Math.max((int) (size / this.loadFactor) + 1, DEFAULT_INITIAL_CAPACITY));
        for (int i = 0; i < size; i++)
        {
            this.add((T) in.readObject());
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException
    {
        out.writeObject(this.hashingStrategy);
        out.writeInt(this.size());
        out.writeFloat(this.loadFactor);
        for (int i = 0; i < this.table.length; i++)
        {
            Object o = this.table[i];
            if (o != null)
            {
                if (o instanceof ChainedBucket)
                {
                    this.writeExternalChain(out, (ChainedBucket) o);
                }
                else
                {
                    out.writeObject(this.nonSentinel(o));
                }
            }
        }
    }

    private void writeExternalChain(ObjectOutput out, ChainedBucket bucket) throws IOException
    {
        do
        {
            out.writeObject(this.nonSentinel(bucket.zero));
            if (bucket.one == null)
            {
                return;
            }
            out.writeObject(this.nonSentinel(bucket.one));
            if (bucket.two == null)
            {
                return;
            }
            out.writeObject(this.nonSentinel(bucket.two));
            if (bucket.three == null)
            {
                return;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                continue;
            }
            out.writeObject(this.nonSentinel(bucket.three));
            return;
        }
        while (true);
    }

    public boolean removeAll(Collection<?> collection)
    {
        return this.removeAllIterable(collection);
    }

    public boolean removeAllIterable(Iterable<?> iterable)
    {
        boolean changed = false;
        for (Object each : iterable)
        {
            changed |= this.remove(each);
        }
        return changed;
    }

    private void addIfFound(T key, UnifiedSetWithHashingStrategy<T> other)
    {
        int index = this.index(key);

        Object cur = this.table[index];
        if (cur == null)
        {
            return;
        }
        if (cur instanceof ChainedBucket)
        {
            this.addIfFoundFromChain((ChainedBucket) cur, key, other);
            return;
        }
        if (this.nonNullTableObjectEquals(cur, key))
        {
            other.add(this.nonSentinel(cur));
        }
    }

    private void addIfFoundFromChain(ChainedBucket bucket, T key, UnifiedSetWithHashingStrategy<T> other)
    {
        do
        {
            if (this.nonNullTableObjectEquals(bucket.zero, key))
            {
                other.add(this.nonSentinel(bucket.zero));
                return;
            }
            if (bucket.one == null)
            {
                return;
            }
            if (this.nonNullTableObjectEquals(bucket.one, key))
            {
                other.add(this.nonSentinel(bucket.one));
                return;
            }
            if (bucket.two == null)
            {
                return;
            }
            if (this.nonNullTableObjectEquals(bucket.two, key))
            {
                other.add(this.nonSentinel(bucket.two));
                return;
            }
            if (bucket.three == null)
            {
                return;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                continue;
            }
            if (this.nonNullTableObjectEquals(bucket.three, key))
            {
                other.add(this.nonSentinel(bucket.three));
                return;
            }
            return;
        }
        while (true);
    }

    public boolean retainAll(Collection<?> collection)
    {
        return this.retainAllIterable(collection);
    }

    public boolean retainAllIterable(Iterable<?> iterable)
    {
        if (iterable instanceof Set)
        {
            return this.retainAllFromSet((Set<?>) iterable);
        }
        return this.retainAllFromNonSet(iterable);
    }

    private boolean retainAllFromNonSet(Iterable<?> iterable)
    {
        int retainedSize = Iterate.sizeOf(iterable);
        UnifiedSetWithHashingStrategy<T> retainedCopy = new UnifiedSetWithHashingStrategy<T>(this.hashingStrategy, retainedSize, this.loadFactor);
        for (Object key : iterable)
        {
            this.addIfFound((T) key, retainedCopy);
        }
        if (retainedCopy.size() < this.size())
        {
            this.maxSize = retainedCopy.maxSize;
            this.occupied = retainedCopy.occupied;
            this.table = retainedCopy.table;
            return true;
        }
        return false;
    }

    private boolean retainAllFromSet(Set<?> collection)
    {
        // TODO: turn iterator into a loop
        boolean result = false;
        Iterator<T> e = this.iterator();
        while (e.hasNext())
        {
            if (!collection.contains(e.next()))
            {
                e.remove();
                result = true;
            }
        }
        return result;
    }

    @Override
    public UnifiedSetWithHashingStrategy<T> clone()
    {
        return new UnifiedSetWithHashingStrategy<T>(this.hashingStrategy, this);
    }

    @Override
    public Object[] toArray()
    {
        Object[] result = new Object[this.occupied];
        this.copyToArray(result);
        return result;
    }

    private void copyToArray(Object[] result)
    {
        Object[] table = this.table;
        int count = 0;
        for (int i = 0; i < table.length; i++)
        {
            Object cur = table[i];
            if (cur != null)
            {
                if (cur instanceof ChainedBucket)
                {
                    ChainedBucket bucket = (ChainedBucket) cur;
                    count = this.copyBucketToArray(result, bucket, count);
                }
                else
                {
                    result[count++] = this.nonSentinel(cur);
                }
            }
        }
    }

    private int copyBucketToArray(Object[] result, ChainedBucket bucket, int count)
    {
        do
        {
            result[count++] = this.nonSentinel(bucket.zero);
            if (bucket.one == null)
            {
                break;
            }
            result[count++] = this.nonSentinel(bucket.one);
            if (bucket.two == null)
            {
                break;
            }
            result[count++] = this.nonSentinel(bucket.two);
            if (bucket.three == null)
            {
                break;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                continue;
            }
            result[count++] = this.nonSentinel(bucket.three);
            break;
        }
        while (true);
        return count;
    }

    @Override
    public <T> T[] toArray(T[] array)
    {
        int size = this.size();
        T[] result = array.length < size
                ? (T[]) Array.newInstance(array.getClass().getComponentType(), size)
                : array;

        this.copyToArray(result);
        if (size < result.length)
        {
            result[size] = null;
        }
        return result;
    }

    public Iterator<T> iterator()
    {
        return new PositionalIterator();
    }

    protected class PositionalIterator implements Iterator<T>
    {
        protected int count;
        protected int position;
        protected int chainPosition;
        protected boolean lastReturned;

        public boolean hasNext()
        {
            return this.count < UnifiedSetWithHashingStrategy.this.size();
        }

        public void remove()
        {
            if (!this.lastReturned)
            {
                throw new IllegalStateException("next() must be called as many times as remove()");
            }
            this.count--;
            UnifiedSetWithHashingStrategy.this.occupied--;

            if (this.chainPosition != 0)
            {
                this.removeFromChain();
                return;
            }

            int pos = this.position - 1;
            Object key = UnifiedSetWithHashingStrategy.this.table[pos];
            if (key instanceof ChainedBucket)
            {
                this.removeLastFromChain((ChainedBucket) key, pos);
                return;
            }
            UnifiedSetWithHashingStrategy.this.table[pos] = null;
            this.position = pos;
            this.lastReturned = false;
        }

        protected void removeFromChain()
        {
            ChainedBucket chain = (ChainedBucket) UnifiedSetWithHashingStrategy.this.table[this.position];
            chain.remove(--this.chainPosition);
            this.lastReturned = false;
        }

        protected void removeLastFromChain(ChainedBucket bucket, int tableIndex)
        {
            bucket.removeLast(0);
            if (bucket.zero == null)
            {
                UnifiedSetWithHashingStrategy.this.table[tableIndex] = null;
            }
            this.lastReturned = false;
        }

        protected T nextFromChain()
        {
            ChainedBucket bucket = (ChainedBucket) UnifiedSetWithHashingStrategy.this.table[this.position];
            Object cur = bucket.get(this.chainPosition);
            this.chainPosition++;
            if (bucket.get(this.chainPosition) == null)
            {
                this.chainPosition = 0;
                this.position++;
            }
            this.lastReturned = true;
            return UnifiedSetWithHashingStrategy.this.nonSentinel(cur);
        }

        public T next()
        {
            if (!this.hasNext())
            {
                throw new NoSuchElementException("next() called, but the iterator is exhausted");
            }
            this.count++;
            Object[] table = UnifiedSetWithHashingStrategy.this.table;
            if (this.chainPosition != 0)
            {
                return this.nextFromChain();
            }
            while (table[this.position] == null)
            {
                this.position++;
            }
            Object cur = table[this.position];
            if (cur instanceof ChainedBucket)
            {
                return this.nextFromChain();
            }
            this.position++;
            this.lastReturned = true;
            return UnifiedSetWithHashingStrategy.this.nonSentinel(cur);
        }
    }

    private static final class ChainedBucket
    {
        private Object zero;
        private Object one;
        private Object two;
        private Object three;

        private ChainedBucket()
        {
        }

        private ChainedBucket(Object first, Object second)
        {
            this.zero = first;
            this.one = second;
        }

        public void remove(int i)
        {
            if (i > 3)
            {
                this.removeLongChain(this, i - 3);
            }
            else
            {
                switch (i)
                {
                    case 0:
                        this.zero = this.removeLast(0);
                        return;
                    case 1:
                        this.one = this.removeLast(1);
                        return;
                    case 2:
                        this.two = this.removeLast(2);
                        return;
                    case 3:
                        if (this.three instanceof ChainedBucket)
                        {
                            this.removeLongChain(this, i - 3);
                            return;
                        }
                        this.three = null;
                        return;
                    default:
                        throw new AssertionError();
                }
            }
        }

        private void removeLongChain(ChainedBucket oldBucket, int i)
        {
            do
            {
                ChainedBucket bucket = (ChainedBucket) oldBucket.three;
                switch (i)
                {
                    case 0:
                        bucket.zero = bucket.removeLast(0);
                        return;
                    case 1:
                        bucket.one = bucket.removeLast(1);
                        return;
                    case 2:
                        bucket.two = bucket.removeLast(2);
                        return;
                    case 3:
                        if (bucket.three instanceof ChainedBucket)
                        {
                            i -= 3;
                            oldBucket = bucket;
                            continue;
                        }
                        bucket.three = null;
                        return;
                    default:
                        throw new AssertionError();
                }
            }
            while (true);
        }

        public Object get(int i)
        {
            ChainedBucket bucket = this;
            while (i > 3 && bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                i -= 3;
            }
            do
            {
                switch (i)
                {
                    case 0:
                        return bucket.zero;
                    case 1:
                        return bucket.one;
                    case 2:
                        return bucket.two;
                    case 3:
                        if (bucket.three instanceof ChainedBucket)
                        {
                            i -= 3;
                            bucket = (ChainedBucket) bucket.three;
                            continue;
                        }
                        return bucket.three;
                    case 4:
                        return null; // this happens when a bucket is exactly full and we're iterating
                    default:
                        throw new AssertionError();
                }
            }
            while (true);
        }

        public Object removeLast(int cur)
        {
            if (this.three instanceof ChainedBucket)
            {
                return this.removeLast(this);
            }
            if (this.three != null)
            {
                Object result = this.three;
                this.three = null;
                return cur == 3 ? null : result;
            }
            if (this.two != null)
            {
                Object result = this.two;
                this.two = null;
                return cur == 2 ? null : result;
            }
            if (this.one != null)
            {
                Object result = this.one;
                this.one = null;
                return cur == 1 ? null : result;
            }
            this.zero = null;
            return null;
        }

        private Object removeLast(ChainedBucket oldBucket)
        {
            do
            {
                ChainedBucket bucket = (ChainedBucket) oldBucket.three;
                if (bucket.three instanceof ChainedBucket)
                {
                    oldBucket = bucket;
                    continue;
                }
                if (bucket.three != null)
                {
                    Object result = bucket.three;
                    bucket.three = null;
                    return result;
                }
                if (bucket.two != null)
                {
                    Object result = bucket.two;
                    bucket.two = null;
                    return result;
                }
                if (bucket.one != null)
                {
                    Object result = bucket.one;
                    bucket.one = null;
                    return result;
                }
                Object result = bucket.zero;
                oldBucket.three = null;
                return result;
            }
            while (true);
        }

        public ChainedBucket copy()
        {
            ChainedBucket result = new ChainedBucket();
            ChainedBucket dest = result;
            ChainedBucket src = this;
            do
            {
                dest.zero = src.zero;
                dest.one = src.one;
                dest.two = src.two;
                if (src.three instanceof ChainedBucket)
                {
                    dest.three = new ChainedBucket();
                    src = (ChainedBucket) src.three;
                    dest = (ChainedBucket) dest.three;
                    continue;
                }
                dest.three = src.three;
                return result;
            }
            while (true);
        }
    }

    public <V> UnifiedSetWithHashingStrategyMultimap<V, T> groupBy(
            Function<? super T, ? extends V> function)
    {
        return this.groupBy(function, UnifiedSetWithHashingStrategyMultimap.<V, T>newMultimap(this.hashingStrategy));
    }

    public <V> UnifiedSetMultimap<V, T> groupByEach(
            Function<? super T, ? extends Iterable<V>> function)
    {
        return this.groupByEach(function, UnifiedSetMultimap.<V, T>newMultimap());
    }

    public <V> MutableMap<V, T> groupByUniqueKey(
            Function<? super T, ? extends V> function)
    {
        return this.groupByUniqueKey(function, UnifiedMap.<V, T>newMap());
    }

    /**
     * @deprecated in 6.0. Use {@link OrderedIterable#zip(Iterable)} instead.
     */
    @Deprecated
    public <S> MutableSet<Pair<T, S>> zip(Iterable<S> that)
    {
        return this.zip(that, UnifiedSet.<Pair<T, S>>newSet());
    }

    /**
     * @deprecated in 6.0. Use {@link OrderedIterable#zipWithIndex()} instead.
     */
    @Deprecated
    public MutableSet<Pair<T, Integer>> zipWithIndex()
    {
        return this.zipWithIndex(UnifiedSet.<Pair<T, Integer>>newSet());
    }

    public RichIterable<RichIterable<T>> chunk(int size)
    {
        return MutableCollectionIterate.chunk(this, size);
    }

    public MutableSet<T> union(SetIterable<? extends T> set)
    {
        return SetIterables.unionInto(this, set, this.newEmpty());
    }

    public <R extends Set<T>> R unionInto(SetIterable<? extends T> set, R targetSet)
    {
        return SetIterables.unionInto(this, set, targetSet);
    }

    public MutableSet<T> intersect(SetIterable<? extends T> set)
    {
        return SetIterables.intersectInto(this, set, this.newEmpty());
    }

    public <R extends Set<T>> R intersectInto(SetIterable<? extends T> set, R targetSet)
    {
        return SetIterables.intersectInto(this, set, targetSet);
    }

    public MutableSet<T> difference(SetIterable<? extends T> subtrahendSet)
    {
        return SetIterables.differenceInto(this, subtrahendSet, this.newEmpty());
    }

    public <R extends Set<T>> R differenceInto(SetIterable<? extends T> subtrahendSet, R targetSet)
    {
        return SetIterables.differenceInto(this, subtrahendSet, targetSet);
    }

    public MutableSet<T> symmetricDifference(SetIterable<? extends T> setB)
    {
        return SetIterables.symmetricDifferenceInto(this, setB, this.newEmpty());
    }

    public <R extends Set<T>> R symmetricDifferenceInto(SetIterable<? extends T> set, R targetSet)
    {
        return SetIterables.symmetricDifferenceInto(this, set, targetSet);
    }

    public boolean isSubsetOf(SetIterable<? extends T> candidateSuperset)
    {
        return SetIterables.isSubsetOf(this, candidateSuperset);
    }

    public boolean isProperSubsetOf(SetIterable<? extends T> candidateSuperset)
    {
        return SetIterables.isProperSubsetOf(this, candidateSuperset);
    }

    public MutableSet<UnsortedSetIterable<T>> powerSet()
    {
        return (MutableSet<UnsortedSetIterable<T>>) (MutableSet<?>) SetIterables.powerSet(this);
    }

    public <B> LazyIterable<Pair<T, B>> cartesianProduct(SetIterable<B> set)
    {
        return SetIterables.cartesianProduct(this, set);
    }

    public T get(T key)
    {
        int index = this.index(key);
        Object cur = this.table[index];

        if (cur == null)
        {
            return null;
        }
        if (cur instanceof ChainedBucket)
        {
            return this.chainedGet(key, (ChainedBucket) cur);
        }
        if (this.nonNullTableObjectEquals(cur, key))
        {
            return (T) cur;
        }
        return null;
    }

    private T chainedGet(T key, ChainedBucket bucket)
    {
        do
        {
            if (this.nonNullTableObjectEquals(bucket.zero, key))
            {
                return this.nonSentinel(bucket.zero);
            }
            if (bucket.one == null)
            {
                return null;
            }
            if (this.nonNullTableObjectEquals(bucket.one, key))
            {
                return this.nonSentinel(bucket.one);
            }
            if (bucket.two == null)
            {
                return null;
            }
            if (this.nonNullTableObjectEquals(bucket.two, key))
            {
                return this.nonSentinel(bucket.two);
            }
            if (bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                continue;
            }
            if (bucket.three == null)
            {
                return null;
            }
            if (this.nonNullTableObjectEquals(bucket.three, key))
            {
                return this.nonSentinel(bucket.three);
            }
            return null;
        }
        while (true);
    }

    public T put(T key)
    {
        int index = this.index(key);
        Object cur = this.table[index];

        if (cur == null)
        {
            this.table[index] = UnifiedSetWithHashingStrategy.toSentinelIfNull(key);
            if (++this.occupied > this.maxSize)
            {
                this.rehash();
            }
            return key;
        }

        if (cur instanceof ChainedBucket || !this.nonNullTableObjectEquals(cur, key))
        {
            return this.chainedPut(key, index);
        }
        return this.nonSentinel(cur);
    }

    private T chainedPut(T key, int index)
    {
        if (this.table[index] instanceof ChainedBucket)
        {
            ChainedBucket bucket = (ChainedBucket) this.table[index];
            do
            {
                if (this.nonNullTableObjectEquals(bucket.zero, key))
                {
                    return this.nonSentinel(bucket.zero);
                }
                if (bucket.one == null)
                {
                    bucket.one = UnifiedSetWithHashingStrategy.toSentinelIfNull(key);
                    if (++this.occupied > this.maxSize)
                    {
                        this.rehash();
                    }
                    return key;
                }
                if (this.nonNullTableObjectEquals(bucket.one, key))
                {
                    return this.nonSentinel(bucket.one);
                }
                if (bucket.two == null)
                {
                    bucket.two = UnifiedSetWithHashingStrategy.toSentinelIfNull(key);
                    if (++this.occupied > this.maxSize)
                    {
                        this.rehash();
                    }
                    return key;
                }
                if (this.nonNullTableObjectEquals(bucket.two, key))
                {
                    return this.nonSentinel(bucket.two);
                }
                if (bucket.three instanceof ChainedBucket)
                {
                    bucket = (ChainedBucket) bucket.three;
                    continue;
                }
                if (bucket.three == null)
                {
                    bucket.three = UnifiedSetWithHashingStrategy.toSentinelIfNull(key);
                    if (++this.occupied > this.maxSize)
                    {
                        this.rehash();
                    }
                    return key;
                }
                if (this.nonNullTableObjectEquals(bucket.three, key))
                {
                    return this.nonSentinel(bucket.three);
                }
                bucket.three = new ChainedBucket(bucket.three, key);
                if (++this.occupied > this.maxSize)
                {
                    this.rehash();
                }
                return key;
            }
            while (true);
        }
        ChainedBucket newBucket = new ChainedBucket(this.table[index], key);
        this.table[index] = newBucket;
        if (++this.occupied > this.maxSize)
        {
            this.rehash();
        }
        return key;
    }

    public T removeFromPool(T key)
    {
        int index = this.index(key);
        Object cur = this.table[index];
        if (cur == null)
        {
            return null;
        }
        if (cur instanceof ChainedBucket)
        {
            return this.removeFromChainForPool((ChainedBucket) cur, key, index);
        }
        if (this.nonNullTableObjectEquals(cur, key))
        {
            this.table[index] = null;
            this.occupied--;
            return this.nonSentinel(cur);
        }
        return null;
    }

    private T removeFromChainForPool(ChainedBucket bucket, T key, int index)
    {
        if (this.nonNullTableObjectEquals(bucket.zero, key))
        {
            Object result = bucket.zero;
            bucket.zero = bucket.removeLast(0);
            if (bucket.zero == null)
            {
                this.table[index] = null;
            }
            this.occupied--;
            return this.nonSentinel(result);
        }
        if (bucket.one == null)
        {
            return null;
        }
        if (this.nonNullTableObjectEquals(bucket.one, key))
        {
            Object result = bucket.one;
            bucket.one = bucket.removeLast(1);
            this.occupied--;
            return this.nonSentinel(result);
        }
        if (bucket.two == null)
        {
            return null;
        }
        if (this.nonNullTableObjectEquals(bucket.two, key))
        {
            Object result = bucket.two;
            bucket.two = bucket.removeLast(2);
            this.occupied--;
            return this.nonSentinel(result);
        }
        if (bucket.three == null)
        {
            return null;
        }
        if (bucket.three instanceof ChainedBucket)
        {
            return this.removeDeepChainForPool(bucket, key);
        }
        if (this.nonNullTableObjectEquals(bucket.three, key))
        {
            Object result = bucket.three;
            bucket.three = bucket.removeLast(3);
            this.occupied--;
            return this.nonSentinel(result);
        }
        return null;
    }

    private T removeDeepChainForPool(ChainedBucket oldBucket, T key)
    {
        do
        {
            ChainedBucket bucket = (ChainedBucket) oldBucket.three;
            if (this.nonNullTableObjectEquals(bucket.zero, key))
            {
                Object result = bucket.zero;
                bucket.zero = bucket.removeLast(0);
                if (bucket.zero == null)
                {
                    oldBucket.three = null;
                }
                this.occupied--;
                return this.nonSentinel(result);
            }
            if (bucket.one == null)
            {
                return null;
            }
            if (this.nonNullTableObjectEquals(bucket.one, key))
            {
                Object result = bucket.one;
                bucket.one = bucket.removeLast(1);
                this.occupied--;
                return this.nonSentinel(result);
            }
            if (bucket.two == null)
            {
                return null;
            }
            if (this.nonNullTableObjectEquals(bucket.two, key))
            {
                Object result = bucket.two;
                bucket.two = bucket.removeLast(2);
                this.occupied--;
                return this.nonSentinel(result);
            }
            if (bucket.three == null)
            {
                return null;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                oldBucket = bucket;
                continue;
            }
            if (this.nonNullTableObjectEquals(bucket.three, key))
            {
                Object result = bucket.three;
                bucket.three = bucket.removeLast(3);
                this.occupied--;
                return this.nonSentinel(result);
            }
            return null;
        }
        while (true);
    }

    private T nonSentinel(Object key)
    {
        return key == NULL_KEY ? null : (T) key;
    }

    private static Object toSentinelIfNull(Object key)
    {
        if (key == null)
        {
            return NULL_KEY;
        }
        return key;
    }

    private boolean nonNullTableObjectEquals(Object cur, T key)
    {
        return cur == key || (cur == NULL_KEY ? key == null : this.hashingStrategy.equals(this.nonSentinel(cur), key));
    }

    public <K2, V> MutableMap<K2, V> aggregateInPlaceBy(
            Function<? super T, ? extends K2> groupBy,
            Function0<? extends V> zeroValueFactory,
            Procedure2<? super V, ? super T> mutatingAggregator)
    {
        MutableMap<K2, V> map = UnifiedMap.newMap();
        this.forEach(new MutatingAggregationProcedure<T, K2, V>(map, groupBy, zeroValueFactory, mutatingAggregator));
        return map;
    }

    public <K2, V> MutableMap<K2, V> aggregateBy(
            Function<? super T, ? extends K2> groupBy,
            Function0<? extends V> zeroValueFactory,
            Function2<? super V, ? super T, ? extends V> nonMutatingAggregator)
    {
        MutableMap<K2, V> map = UnifiedMap.newMap();
        this.forEach(new NonMutatingAggregationProcedure<T, K2, V>(map, groupBy, zeroValueFactory, nonMutatingAggregator));
        return map;
    }

    @Beta
    public ParallelUnsortedSetIterable<T> asParallel(ExecutorService executorService, int batchSize)
    {
        if (executorService == null)
        {
            throw new NullPointerException();
        }
        if (batchSize < 1)
        {
            throw new IllegalArgumentException();
        }
        return new UnifiedSetParallelUnsortedIterable(executorService, batchSize);
    }

    private final class UnifiedUnsortedSetBatch extends AbstractBatch<T> implements RootUnsortedSetBatch<T>
    {
        private final int chunkStartIndex;
        private final int chunkEndIndex;

        private UnifiedUnsortedSetBatch(int chunkStartIndex, int chunkEndIndex)
        {
            this.chunkStartIndex = chunkStartIndex;
            this.chunkEndIndex = chunkEndIndex;
        }

        public void forEach(Procedure<? super T> procedure)
        {
            for (int i = this.chunkStartIndex; i < this.chunkEndIndex; i++)
            {
                Object cur = UnifiedSetWithHashingStrategy.this.table[i];
                if (cur instanceof ChainedBucket)
                {
                    UnifiedSetWithHashingStrategy.this.chainedForEach((ChainedBucket) cur, procedure);
                }
                else if (cur != null)
                {
                    procedure.value(UnifiedSetWithHashingStrategy.this.nonSentinel(cur));
                }
            }
        }

        public boolean anySatisfy(Predicate<? super T> predicate)
        {
            return UnifiedSetWithHashingStrategy.this.shortCircuit(predicate, true, true, false, this.chunkStartIndex, this.chunkEndIndex);
        }

        public boolean allSatisfy(Predicate<? super T> predicate)
        {
            return UnifiedSetWithHashingStrategy.this.shortCircuit(predicate, false, false, true, this.chunkStartIndex, this.chunkEndIndex);
        }

        public T detect(Predicate<? super T> predicate)
        {
            return UnifiedSetWithHashingStrategy.this.detect(predicate, this.chunkStartIndex, this.chunkEndIndex);
        }

        public UnsortedSetBatch<T> select(Predicate<? super T> predicate)
        {
            return new SelectUnsortedSetBatch<T>(this, predicate);
        }

        public <V> UnsortedSetBatch<V> collect(Function<? super T, ? extends V> function)
        {
            return new CollectUnsortedSetBatch<T, V>(this, function);
        }
    }

    private final class UnifiedSetParallelUnsortedIterable extends AbstractParallelUnsortedSetIterable<T, RootUnsortedSetBatch<T>>
    {
        private final ExecutorService executorService;
        private final int batchSize;

        private UnifiedSetParallelUnsortedIterable(ExecutorService executorService, int batchSize)
        {
            this.executorService = executorService;
            this.batchSize = batchSize;
        }

        @Override
        public ExecutorService getExecutorService()
        {
            return this.executorService;
        }

        @Override
        public int getBatchSize()
        {
            return this.batchSize;
        }

        @Override
        public LazyIterable<RootUnsortedSetBatch<T>> split()
        {
            return new UnifiedSetParallelSplitLazyIterable();
        }

        public void forEach(Procedure<? super T> procedure)
        {
            AbstractParallelIterable.forEach(this, procedure);
        }

        public boolean anySatisfy(Predicate<? super T> predicate)
        {
            return AbstractParallelIterable.anySatisfy(this, predicate);
        }

        public boolean allSatisfy(Predicate<? super T> predicate)
        {
            return AbstractParallelIterable.allSatisfy(this, predicate);
        }

        public T detect(Predicate<? super T> predicate)
        {
            return AbstractParallelIterable.detect(this, predicate);
        }

        @Override
        public Object[] toArray()
        {
            // TODO: Implement in parallel
            return UnifiedSetWithHashingStrategy.this.toArray();
        }

        @Override
        public <E> E[] toArray(E[] array)
        {
            // TODO: Implement in parallel
            return UnifiedSetWithHashingStrategy.this.toArray(array);
        }

        private class UnifiedSetParallelSplitIterator implements Iterator<RootUnsortedSetBatch<T>>
        {
            protected int chunkIndex;

            public boolean hasNext()
            {
                return this.chunkIndex * UnifiedSetParallelUnsortedIterable.this.batchSize < UnifiedSetWithHashingStrategy.this.table.length;
            }

            public RootUnsortedSetBatch<T> next()
            {
                int chunkStartIndex = this.chunkIndex * UnifiedSetParallelUnsortedIterable.this.batchSize;
                int chunkEndIndex = (this.chunkIndex + 1) * UnifiedSetParallelUnsortedIterable.this.batchSize;
                int truncatedChunkEndIndex = Math.min(chunkEndIndex, UnifiedSetWithHashingStrategy.this.table.length);
                this.chunkIndex++;
                return new UnifiedUnsortedSetBatch(chunkStartIndex, truncatedChunkEndIndex);
            }

            public void remove()
            {
                throw new UnsupportedOperationException("Cannot call remove() on " + this.getClass().getSimpleName());
            }
        }

        private class UnifiedSetParallelSplitLazyIterable
                extends AbstractLazyIterable<RootUnsortedSetBatch<T>>
        {
            public void each(Procedure<? super RootUnsortedSetBatch<T>> procedure)
            {
                for (RootUnsortedSetBatch<T> chunk : this)
                {
                    procedure.value(chunk);
                }
            }

            public Iterator<RootUnsortedSetBatch<T>> iterator()
            {
                return new UnifiedSetParallelSplitIterator();
            }
        }
    }
}
