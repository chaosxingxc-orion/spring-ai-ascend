"""日志工具模块：提供统一的日志记录功能。

设计说明
--------
- 同一天的日志追加写入同一个日志文件（logs/interact_skill_YYYYMMDD.log）
- 如果日志文件超过50MB，自动转储为带时间戳的备份文件，然后创建新文件继续追加
- 如果文件写入失败（权限不足、磁盘满等），自动回退到 print 输出
- 支持日志级别：DEBUG、INFO、WARNING、ERROR
- 线程安全设计，使用锁保护文件写入
"""

import os
import sys
import time
import threading
from typing import Any

# 日志目录：保存在 skill 根目录下
_LOG_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), "logs")

# 生成日期格式的日志文件名（模块加载时生成，同一天使用同一个文件）
_START_DATE = time.strftime("%Y%m%d", time.localtime())
_LOG_FILE = os.path.join(_LOG_DIR, f"interact_skill_{_START_DATE}.log")

# 文件大小阈值：50MB
_MAX_FILE_SIZE = 50 * 1024 * 1024  # 52428800 bytes

# 文件写入锁，确保线程安全
_file_lock = threading.Lock()

# 是否可以写入文件（首次检测后缓存结果）
_file_writable = None


def _is_file_writable() -> bool:
    """检测日志文件是否可写入。
    
    返回缓存的结果，如果尚未检测则进行检测。
    """
    global _file_writable
    if _file_writable is not None:
        return _file_writable
    
    try:
        # 创建日志目录
        os.makedirs(_LOG_DIR, exist_ok=True)
        
        # 尝试追加写入启动标识（不覆盖已有文件）
        startup_content = f"[{_get_timestamp()}] [INFO] ========== Logging started ==========\n"
        with open(_LOG_FILE, "a", encoding="utf-8") as f:
            f.write(startup_content)
        
        _file_writable = True
    except Exception:
        _file_writable = False
    
    return _file_writable


def _get_timestamp() -> str:
    """获取当前时间戳字符串，格式：YYYY-MM-DD HH:MM:SS"""
    return time.strftime("%Y-%m-%d %H:%M:%S", time.localtime())


def _rotate_log_file() -> None:
    """检查并转储日志文件。
    
    如果当前日志文件大小超过阈值（50MB），将其重命名为带时间戳的备份文件，
    然后创建新的日志文件。
    """
    global _LOG_FILE
    try:
        if os.path.exists(_LOG_FILE):
            file_size = os.path.getsize(_LOG_FILE)
            if file_size >= _MAX_FILE_SIZE:
                # 生成转储文件名：interact_skill_YYYYMMDD_HHMMSS.log
                timestamp = time.strftime("%H%M%S", time.localtime())
                backup_file = os.path.join(_LOG_DIR, f"interact_skill_{_START_DATE}_{timestamp}.log")
                os.rename(_LOG_FILE, backup_file)
                # 创建新的空日志文件并写入启动标识
                with open(_LOG_FILE, "a", encoding="utf-8") as f:
                    f.write(f"[{_get_timestamp()}] [INFO] ========== Logging started (rotated) ==========\n")
    except Exception:
        pass


def _log_to_file(level: str, message: str) -> bool:
    """尝试将日志写入文件。
    
    Returns:
        True: 写入成功
        False: 写入失败
    """
    if not _is_file_writable():
        return False
    
    try:
        with _file_lock:
            # 写入前检查是否需要转储
            _rotate_log_file()
            with open(_LOG_FILE, "a", encoding="utf-8") as f:
                f.write(f"[{_get_timestamp()}] [{level}] {message}\n")
        return True
    except Exception:
        # 写入失败，标记文件不可写
        global _file_writable
        _file_writable = False
        return False


def log_debug(message: Any) -> None:
    """DEBUG 级别日志"""
    msg_str = str(message)
    if not _log_to_file("DEBUG", msg_str):
        print(f"[{_get_timestamp()}] [DEBUG] {msg_str}", file=sys.stderr)


def log_info(message: Any) -> None:
    """INFO 级别日志"""
    msg_str = str(message)
    if not _log_to_file("INFO", msg_str):
        print(f"[{_get_timestamp()}] [INFO] {msg_str}", file=sys.stderr)


def log_warning(message: Any) -> None:
    """WARNING 级别日志"""
    msg_str = str(message)
    if not _log_to_file("WARNING", msg_str):
        print(f"[{_get_timestamp()}] [WARNING] {msg_str}", file=sys.stderr)


def log_error(message: Any) -> None:
    """ERROR 级别日志"""
    msg_str = str(message)
    if not _log_to_file("ERROR", msg_str):
        print(f"[{_get_timestamp()}] [ERROR] {msg_str}", file=sys.stderr)


def log_stdout(message: Any) -> None:
    """标准输出日志（用于输出最终结果）
    
    此函数用于输出脚本的最终结果，始终输出到 stdout。
    """
    print(message)
