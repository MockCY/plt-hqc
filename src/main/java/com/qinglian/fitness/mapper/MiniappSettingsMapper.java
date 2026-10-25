package com.qinglian.fitness.mapper;

import com.qinglian.fitness.settings.MiniappSettings;
import org.apache.ibatis.annotations.Mapper;

/** 通过 XML 读写唯一的小程序展示配置。 */
@Mapper
public interface MiniappSettingsMapper {
    /** 读取设置，未初始化返回 null。 */
    MiniappSettings find();

    /** 版本匹配才更新，返回实际受影响行数。 */
    int update(MiniappSettings settings);
}
