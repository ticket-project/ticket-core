package com.ticket.core.infra.lock;

import com.ticket.core.support.lock.DistributedLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DistributedLockAopTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @Test
    void default_lease_uses_watchdog_try_lock() throws InterruptedException {
        when(redissonClient.getLock("LOCK:test:key")).thenReturn(lock);
        when(lock.tryLock(100L, TimeUnit.MILLISECONDS)).thenReturn(true);
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(new LockedService());
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAspect(new DistributedLockAop(redissonClient));
        LockedService proxy = proxyFactory.getProxy();

        proxy.execute("key");

        verify(lock).tryLock(100L, TimeUnit.MILLISECONDS);
        verify(lock).unlock();
    }

    static class LockedService {

        @DistributedLock(prefix = "test", dynamicKey = "#key", waitTime = 100L)
        public void execute(final String key) {
        }
    }
}
