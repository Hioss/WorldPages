package com.hioss.spider.dto;

import java.nio.file.Path;
import java.time.LocalDate;

/**
 * 保存日期的DTO类
 *
 * @author      程春海
 * @version     1.0
 * @since       2025-11-18
 * 
 */
public class PathWithDate {
    Path path;
    LocalDate date;

    public PathWithDate() {
    }

    public PathWithDate(Path path, LocalDate date) {
        this.path = path;
        this.date = date;
    }

    public Path getPath() {
        return path;
    }

    public void setPath(Path path) {
        this.path = path;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

}
