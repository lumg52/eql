package org.n3r.eql.impl;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.n3r.eql.base.EqlResourceLoader;
import org.n3r.eql.parser.EqlBlock;
import org.n3r.eql.util.C;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

@Slf4j @NoArgsConstructor
public class FileEqlResourceLoader extends AbstractEqlResourceLoader {
    static Cache<String, Optional<Map<String, EqlBlock>>> fileCache;
    static LoadingCache<EqlUniqueSqlId, Optional<EqlBlock>> sqlCache;

    static {
        fileCache = CacheBuilder.newBuilder().build();
        sqlCache = EqlResourceLoaderHelper.buildSqlCache(fileCache);
    }

    @Override
    public EqlBlock loadEqlBlock(String sqlClassPath, String sqlId) {
        load(this, sqlClassPath);

        val eqlUniqueSqlId = new EqlUniqueSqlId(sqlClassPath, sqlId);
        val eqlBlock = sqlCache.getUnchecked(eqlUniqueSqlId);
        if (eqlBlock.isPresent()) return eqlBlock.get();

        throw new RuntimeException("unable to find sql id " + eqlUniqueSqlId);
    }

    @Override
    public Map<String, EqlBlock> load(String classPath) {
        return load(this, classPath);
    }

    @SneakyThrows
    private Map<String, EqlBlock> load(final EqlResourceLoader eqlResLoader,
                                       final String sqlClassPath) {
        val valueLoader = new Callable<Optional<Map<String, EqlBlock>>>() {
            @Override
            public Optional<Map<String, EqlBlock>> call() {
                val sqlContent = C.classResourceToString(sqlClassPath);
                if (sqlContent == null) {
                    log.warn("classpath sql {} not found", sqlClassPath);
                    return Optional.absent();
                }

                return Optional.of(EqlResourceLoaderHelper.updateFileCache(
                        sqlContent, eqlResLoader, sqlClassPath, eqlLazyLoad));
            }
        };

        try {
            return fileCache.get(sqlClassPath, valueLoader).orNull();
        } catch (ExecutionException e) {
            throw Throwables.getRootCause(e);
        }
    }


}
