"""会话管理器 - 管理LLM对话历史"""
from typing import Dict, List, Optional
from datetime import datetime, timedelta
import threading


class SessionManager:
    """管理多个会话的对话历史"""
    
    def __init__(self, max_history_length: int = 10, session_timeout_minutes: int = 60):
        """
        初始化会话管理器
        
        Args:
            max_history_length: 每个会话保留的最大历史消息数
            session_timeout_minutes: 会话超时时间（分钟）
        """
        self.sessions: Dict[str, Dict] = {}
        self.max_history_length = max_history_length
        self.session_timeout = timedelta(minutes=session_timeout_minutes)
        self.lock = threading.Lock()
    
    def get_history(self, session_id: str) -> List[Dict]:
        """
        获取指定会话的历史消息
        
        Args:
            session_id: 会话ID
        
        Returns:
            历史消息列表
        """
        with self.lock:
            self._cleanup_expired_sessions()
            
            if session_id not in self.sessions:
                self.sessions[session_id] = {
                    "messages": [],
                    "last_access": datetime.now()
                }
            
            # 更新最后访问时间
            self.sessions[session_id]["last_access"] = datetime.now()
            return self.sessions[session_id]["messages"].copy()
    
    def add_message(self, session_id: str, role: str, content: str):
        """
        添加消息到会话历史
        
        Args:
            session_id: 会话ID
            role: 消息角色（user/assistant）
            content: 消息内容
        """
        with self.lock:
            if session_id not in self.sessions:
                self.sessions[session_id] = {
                    "messages": [],
                    "last_access": datetime.now()
                }
            
            self.sessions[session_id]["messages"].append({
                "role": role,
                "content": content
            })
            
            # 限制历史长度
            if len(self.sessions[session_id]["messages"]) > self.max_history_length:
                self.sessions[session_id]["messages"] = \
                    self.sessions[session_id]["messages"][-self.max_history_length:]
            
            self.sessions[session_id]["last_access"] = datetime.now()
    
    def clear_session(self, session_id: str):
        """
        清除指定会话的历史
        
        Args:
            session_id: 会话ID
        """
        with self.lock:
            if session_id in self.sessions:
                del self.sessions[session_id]
    
    def list_sessions(self) -> List[str]:
        """
        列出所有活跃的会话ID
        
        Returns:
            会话ID列表
        """
        with self.lock:
            self._cleanup_expired_sessions()
            return list(self.sessions.keys())
    
    def get_session_info(self, session_id: str) -> Optional[Dict]:
        """
        获取会话信息
        
        Args:
            session_id: 会话ID
        
        Returns:
            会话信息（消息数量、最后访问时间等）
        """
        with self.lock:
            if session_id not in self.sessions:
                return None
            
            return {
                "session_id": session_id,
                "message_count": len(self.sessions[session_id]["messages"]),
                "last_access": self.sessions[session_id]["last_access"].isoformat()
            }
    
    def _cleanup_expired_sessions(self):
        """清理过期的会话（内部方法）"""
        now = datetime.now()
        expired_sessions = [
            sid for sid, data in self.sessions.items()
            if now - data["last_access"] > self.session_timeout
        ]
        
        for sid in expired_sessions:
            del self.sessions[sid]


# 全局会话管理器实例
_session_manager = SessionManager()


def get_session_manager() -> SessionManager:
    """获取全局会话管理器实例"""
    return _session_manager
