package com.qinglian.fitness.settings;

import com.qinglian.fitness.common.ApiException;
import com.qinglian.fitness.mapper.MiniappSettingsMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证展示配置的空值、失败与并发保护语义，不替代真实数据库验证。 */
class MiniappSettingsServiceTest {
    private final MiniappSettingsMapper mapper = mock(MiniappSettingsMapper.class);
    private final MiniappSettingsService service = new MiniappSettingsService(mapper);

    @Test
    void savesTrimmedVersionAndAdvancesRevision() {
        MiniappSettings settings = settings(" 2.3.4 ", 3L);
        when(mapper.update(settings)).thenReturn(1);
        assertSame(settings, service.update(settings));
        assertEquals("2.3.4", settings.getAppVersion());
        assertEquals(4L, settings.getRevision());
    }

    @Test
    void blankAndNullClearVersion() {
        for (String version : new String[]{"   ", null}) {
            MiniappSettings settings = settings(version, 0L);
            when(mapper.update(settings)).thenReturn(1);
            assertNull(service.update(settings).getAppVersion());
        }
    }

    @Test
    void conflictDoesNotReportSuccessOrAdvanceRevision() {
        MiniappSettings settings = settings("2.3.4", 3L);
        ApiException error = assertThrows(ApiException.class, () -> service.update(settings));
        assertEquals("MINIAPP_SETTINGS_CONFLICT", error.code());
        assertEquals(3L, settings.getRevision());
    }

    @Test
    void missingConfigurationIsAnExplicitFailure() {
        assertEquals("MINIAPP_SETTINGS_NOT_READY", assertThrows(ApiException.class, service::get).code());
    }

    @Test
    void savesThreeIndependentFieldsAndPreservesLineBreaks() {
        MiniappSettings settings = settings(" 2.3.4 ", 3L);
        settings.setVersionNumber(" 2.4.0 ");
        settings.setUpdateContent(" 优化训练\n修复显示问题 ");
        when(mapper.update(settings)).thenReturn(1);
        MiniappSettings saved = service.update(settings);
        assertEquals("2.3.4", saved.getAppVersion());
        assertEquals("2.4.0", saved.getVersionNumber());
        assertEquals("优化训练\n修复显示问题", saved.getUpdateContent());
    }

    @Test
    void distinguishesOmittedFieldsFromExplicitClear() {
        MiniappSettings settings = settings("2.3.4", 0L);
        assertFalse(settings.hasVersionNumber());
        assertFalse(settings.hasUpdateContent());
        settings.setVersionNumber(null);
        settings.setUpdateContent("  ");
        when(mapper.update(settings)).thenReturn(1);
        MiniappSettings saved = service.update(settings);
        assertTrue(saved.hasVersionNumber());
        assertTrue(saved.hasUpdateContent());
        assertNull(saved.getVersionNumber());
        assertNull(saved.getUpdateContent());
    }

    private MiniappSettings settings(String version, long revision) {
        MiniappSettings settings = new MiniappSettings();
        settings.setAppVersion(version);
        settings.setRevision(revision);
        when(mapper.find()).thenReturn(settings);
        return settings;
    }
}
