import React, { useState, useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { 
  Search, Users, MessageSquare, Send, LogOut, 
  PlusCircle, User, Group, ShieldCheck, UserPlus, Info 
} from 'lucide-react';

interface ChatProps {
  onLogout: () => void;
}

export default function Chat({ onLogout }: ChatProps) {
  // Current user details
  const myId = localStorage.getItem('userId') || '';
  const myUsername = localStorage.getItem('username') || '';
  const myEmail = localStorage.getItem('email') || '';
  const myAvatarUrl = localStorage.getItem('avatarUrl') || '';
  const accessToken = localStorage.getItem('accessToken') || '';

  // App States
  const [conversations, setConversations] = useState<any[]>([]); // Groups and selected private users
  const [activeChat, setActiveChat] = useState<any>(null); // Current selected conversation
  const [messages, setMessages] = useState<any[]>([]);
  const [messageText, setMessageText] = useState('');
  
  // Search & Dialog States
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<any[]>([]);
  const [showGroupModal, setShowGroupModal] = useState(false);
  const [groupName, setGroupName] = useState('');
  const [groupDesc, setGroupDesc] = useState('');
  const [showAddMemberModal, setShowAddMemberModal] = useState(false);
  const [memberSearchQuery, setMemberSearchQuery] = useState('');
  const [memberSearchResults, setMemberSearchResults] = useState<any[]>([]);
  
  // System states
  const [isConnected, setIsConnected] = useState(false);
  const stompClientRef = useRef<Client | null>(null);
  const messagesEndRef = useRef<HTMLDivElement | null>(null);

  // 1. Fetch initial groups on load
  const fetchMyGroups = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/groups/my', {
        headers: {
          'Authorization': `Bearer ${accessToken}`,
          'X-User-Id': myId,
          'X-User-Username': myUsername
        }
      });
      if (response.ok) {
        const data = await response.json();
        const formattedGroups = data.map((g: any) => ({
          ...g,
          chatType: 'GROUP'
        }));
        setConversations(prev => {
          // Keep private chats that might be in conversation list
          const privateChats = prev.filter(c => c.chatType === 'PRIVATE');
          return [...privateChats, ...formattedGroups];
        });
      }
    } catch (err) {
      console.error("Error loading groups:", err);
    }
  };

  useEffect(() => {
    fetchMyGroups();
  }, []);

  // 2. Periodic heartbeat to keep user online in Redis (every 20 seconds)
  useEffect(() => {
    const sendHeartbeat = async () => {
      if (!accessToken) return;
      try {
        await fetch('http://localhost:8080/api/users/heartbeat', {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${accessToken}`,
            'X-User-Id': myId,
            'X-User-Username': myUsername
          }
        });
      } catch (err) {
        console.error("Heartbeat failed", err);
      }
    };

    sendHeartbeat(); // First instant call
    const interval = setInterval(sendHeartbeat, 20000);
    return () => clearInterval(interval);
  }, [accessToken, myId, myUsername]);

  // 3. StompJS WebSocket Connection Setup
  useEffect(() => {
    if (!accessToken) return;

    // Use SockJS factory for falling back if native ws fails
    const socketFactory = () => new SockJS('http://localhost:8080/ws');

    const client = new Client({
      webSocketFactory: socketFactory,
      connectHeaders: {
        'Authorization': `Bearer ${accessToken}`
      },
      debug: (str) => {
        console.log("Stomp Debug: " + str);
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000
    });

    client.onConnect = (frame) => {
      console.log('Connected to STOMP Broker:', frame);
      setIsConnected(true);

      // Subscribe to personal queue for private messages
      client.subscribe(`/user/${myId}/queue/messages`, (message) => {
        const msg = JSON.parse(message.body);
        console.log("Received real-time private message:", msg);
        handleIncomingMessage(msg);
      });
    };

    client.onDisconnect = () => {
      console.log('Disconnected from STOMP Broker');
      setIsConnected(false);
    };

    client.onStompError = (frame) => {
      console.error('STOMP Broker Error:', frame);
    };

    client.activate();
    stompClientRef.current = client;

    return () => {
      if (client) {
        client.deactivate();
      }
    };
  }, [accessToken, myId]);

  // 4. Handle active group subscriptions
  // Keep track of active subscriptions to avoid double subscribing
  const groupSubscriptionsRef = useRef<{ [groupId: string]: any }>({});

  useEffect(() => {
    if (!stompClientRef.current || !isConnected || !activeChat) return;

    if (activeChat.chatType === 'GROUP') {
      const gId = activeChat.id;

      // Subscribe if not already subscribed
      if (!groupSubscriptionsRef.current[gId]) {
        console.log("Subscribing to group:", gId);
        const sub = stompClientRef.current.subscribe(`/topic/group/${gId}`, (message) => {
          const msg = JSON.parse(message.body);
          console.log("Received real-time group message:", msg);
          handleIncomingMessage(msg);
        });
        groupSubscriptionsRef.current[gId] = sub;
      }
    }
  }, [activeChat, isConnected]);

  // 5. Handle Incoming Message stream routing
  const handleIncomingMessage = (msg: any) => {
    // If the message is for the currently open chat, append it to messages list
    // Check for PRIVATE chat matching
    const isCurrentPrivate = activeChat && activeChat.chatType === 'PRIVATE' && msg.eventType === 'PRIVATE_MESSAGE' &&
      ((msg.senderId === activeChat.id && msg.receiverId === myId) || 
       (msg.senderId === myId && msg.receiverId === activeChat.id));

    // Check for GROUP chat matching
    const isCurrentGroup = activeChat && activeChat.chatType === 'GROUP' && msg.eventType === 'GROUP_MESSAGE' &&
      msg.groupId === activeChat.id;

    if (isCurrentPrivate || isCurrentGroup) {
      setMessages(prev => {
        // Prevent double insertion
        if (prev.some(m => m.messageId === msg.eventId)) return prev;
        
        return [...prev, {
          messageId: msg.eventId,
          senderId: msg.senderId,
          senderUsername: msg.senderUsername,
          content: msg.content,
          timestamp: msg.timestamp
        }].sort((a,b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime());
      });
    }
  };

  // 6. Fetch chat history when switching active chat
  useEffect(() => {
    if (!activeChat) return;
    setMessages([]);

    const fetchHistory = async () => {
      let url = '';
      if (activeChat.chatType === 'PRIVATE') {
        url = `http://localhost:8080/api/messages/private?senderId=${myId}&receiverId=${activeChat.id}&page=0&size=50`;
      } else {
        url = `http://localhost:8080/api/messages/group/${activeChat.id}?page=0&size=50`;
      }

      try {
        const response = await fetch(url, {
          headers: {
            'Authorization': `Bearer ${accessToken}`,
            'X-User-Id': myId,
            'X-User-Username': myUsername
          }
        });
        if (response.ok) {
          const data = await response.json();
          // Sort messages in ascending order (older first)
          const sortedMsgs = (data.content || []).map((m: any) => ({
            messageId: m.messageId,
            senderId: m.senderId,
            senderUsername: m.senderUsername,
            content: m.content,
            timestamp: m.timestamp
          })).sort((a: any, b: any) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime());

          setMessages(sortedMsgs);
        }
      } catch (err) {
        console.error("Error fetching history:", err);
      }
    };

    fetchHistory();
  }, [activeChat]);

  // 7. Auto-scroll to bottom of messages
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // 8. User searching
  useEffect(() => {
    if (searchQuery.trim().length === 0) {
      setSearchResults([]);
      return;
    }

    const searchUsers = async () => {
      try {
        const response = await fetch(`http://localhost:8080/api/users/search?query=${searchQuery}`, {
          headers: {
            'Authorization': `Bearer ${accessToken}`,
            'X-User-Id': myId,
            'X-User-Username': myUsername
          }
        });
        if (response.ok) {
          const data = await response.json();
          // Exclude myself from search
          setSearchResults(data.filter((u: any) => u.id !== myId));
        }
      } catch (err) {
        console.error("Search failed", err);
      }
    };

    const delayDebounce = setTimeout(searchUsers, 300);
    return () => clearTimeout(delayDebounce);
  }, [searchQuery]);

  // 9. Send Message
  const handleSendMessage = (e: React.FormEvent) => {
    e.preventDefault();
    if (!messageText.trim() || !stompClientRef.current || !isConnected || !activeChat) return;

    const payload: any = {
      senderUsername: myUsername,
      content: messageText.trim(),
    };

    if (activeChat.chatType === 'PRIVATE') {
      payload.type = 'PRIVATE';
      payload.receiverId = activeChat.id;
      stompClientRef.current.publish({
        destination: '/app/chat.sendMessage',
        body: JSON.stringify(payload)
      });
    } else {
      payload.type = 'GROUP';
      payload.groupId = activeChat.id;
      stompClientRef.current.publish({
        destination: '/app/group.sendMessage',
        body: JSON.stringify(payload)
      });
    }

    setMessageText('');
  };

  // 10. Start Private Chat with searched user
  const startPrivateChat = (user: any) => {
    const privateConversation = {
      id: user.id,
      name: user.username,
      description: user.email,
      avatarUrl: user.avatarUrl,
      chatType: 'PRIVATE'
    };

    // Add to conversations if not already there
    setConversations(prev => {
      if (prev.some(c => c.id === user.id && c.chatType === 'PRIVATE')) return prev;
      return [privateConversation, ...prev];
    });

    setActiveChat(privateConversation);
    setSearchQuery('');
  };

  // 11. Create Group Group
  const handleCreateGroup = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!groupName.trim()) return;

    try {
      const response = await fetch('http://localhost:8080/api/groups', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${accessToken}`,
          'X-User-Id': myId,
          'X-User-Username': myUsername
        },
        body: JSON.stringify({ name: groupName, description: groupDesc })
      });

      if (response.ok) {
        const data = await response.json();
        const newG = { ...data, chatType: 'GROUP' };
        setConversations(prev => [newG, ...prev]);
        setActiveChat(newG);
        
        // Reset and close modal
        setGroupName('');
        setGroupDesc('');
        setShowGroupModal(false);
      }
    } catch (err) {
      console.error("Create group failed", err);
    }
  };

  // 12. Search users to add to active group
  useEffect(() => {
    if (memberSearchQuery.trim().length === 0) {
      setMemberSearchResults([]);
      return;
    }

    const searchMembers = async () => {
      try {
        const response = await fetch(`http://localhost:8080/api/users/search?query=${memberSearchQuery}`, {
          headers: {
            'Authorization': `Bearer ${accessToken}`,
            'X-User-Id': myId,
            'X-User-Username': myUsername
          }
        });
        if (response.ok) {
          const data = await response.json();
          setMemberSearchResults(data.filter((u: any) => u.id !== myId));
        }
      } catch (err) {
        console.error(err);
      }
    };

    const delay = setTimeout(searchMembers, 300);
    return () => clearTimeout(delay);
  }, [memberSearchQuery]);

  // 13. Add Member to Group
  const handleAddMember = async (userId: String) => {
    if (!activeChat || activeChat.chatType !== 'GROUP') return;

    try {
      const response = await fetch(`http://localhost:8080/api/groups/${activeChat.id}/members`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${accessToken}`,
          'X-User-Id': myId,
          'X-User-Username': myUsername
        },
        body: JSON.stringify({ userId, role: 'MEMBER' })
      });

      if (response.ok) {
        alert("Member added successfully!");
        setMemberSearchQuery('');
        setMemberSearchResults([]);
        setShowAddMemberModal(false);
      } else {
        const errData = await response.json();
        alert("Failed: " + (errData.error || "Permission denied"));
      }
    } catch (err) {
      console.error(err);
    }
  };

  // 14. Logout logic
  const handleLogoutClick = async () => {
    const refreshToken = localStorage.getItem('refreshToken');
    try {
      await fetch('http://localhost:8080/api/auth/logout', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${accessToken}`
        },
        body: JSON.stringify({ refreshToken })
      });
    } catch (err) {
      console.error("Logout request failed", err);
    } finally {
      localStorage.clear();
      onLogout();
    }
  };

  return (
    <div className="flex h-screen w-screen overflow-hidden text-gray-200">
      {/* Sidebar Panel */}
      <div className="w-80 md:w-96 bg-slate-950/70 border-r border-slate-800/80 flex flex-col h-full shrink-0">
        
        {/* User Card Profile Header */}
        <div className="p-4 border-b border-slate-800/80 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <img 
              src={myAvatarUrl || `https://api.dicebear.com/7.x/bottts/svg?seed=${myUsername}`} 
              alt="Avatar" 
              className="w-10 h-10 rounded-full border border-indigo-500/30 bg-slate-900"
            />
            <div className="text-left">
              <h2 className="text-sm font-semibold text-white tracking-wide truncate max-w-[120px]">{myUsername}</h2>
              <span className="flex items-center gap-1 text-[10px] text-emerald-400 font-semibold uppercase tracking-wider">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
                Connected
              </span>
            </div>
          </div>
          
          <div className="flex gap-2">
            <button 
              onClick={() => setShowGroupModal(true)} 
              title="Create Group"
              className="p-2 hover:bg-slate-800/70 rounded-full text-indigo-400 cursor-pointer transition-colors"
            >
              <PlusCircle className="w-5 h-5" />
            </button>
            <button 
              onClick={handleLogoutClick} 
              title="Log Out"
              className="p-2 hover:bg-slate-800/70 rounded-full text-red-400 cursor-pointer transition-colors"
            >
              <LogOut className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Global User Search Bar */}
        <div className="p-3">
          <div className="relative">
            <Search className="w-4 h-4 text-gray-500 absolute left-3 top-3" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search users..."
              className="w-full bg-slate-900/60 border border-slate-800 rounded-xl pl-9 pr-4 py-2 text-sm text-white focus:outline-none focus:border-indigo-500 transition-colors"
            />
          </div>

          {/* Search Dropdown Panel */}
          {searchResults.length > 0 && (
            <div className="glass mt-2 max-h-60 overflow-y-auto rounded-xl shadow-xl border border-slate-800 absolute z-35 w-[296px] md:w-[360px] divide-y divide-slate-800/50">
              {searchResults.map((user: any) => (
                <div 
                  key={user.id} 
                  onClick={() => startPrivateChat(user)}
                  className="flex items-center gap-3 p-3 hover:bg-indigo-600/10 cursor-pointer transition-all duration-200 text-left"
                >
                  <img src={user.avatarUrl} alt="Avatar" className="w-8 h-8 rounded-full bg-slate-900 border border-indigo-500/20" />
                  <div>
                    <h4 className="text-sm font-semibold text-white">{user.username}</h4>
                    <p className="text-[11px] text-gray-400">{user.email}</p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Conversation List */}
        <div className="flex-1 overflow-y-auto divide-y divide-slate-900/50">
          <div className="px-4 py-2 text-[11px] font-bold uppercase tracking-wider text-gray-500 text-left">Conversations</div>
          {conversations.length === 0 ? (
            <div className="text-gray-500 text-sm mt-8 text-center px-4">
              No chat logs yet. Search a user or create a group to start messaging!
            </div>
          ) : (
            conversations.map((c: any) => {
              const isSelected = activeChat && activeChat.id === c.id && activeChat.chatType === c.chatType;
              return (
                <div
                  key={c.id + '-' + c.chatType}
                  onClick={() => setActiveChat(c)}
                  className={`flex items-center gap-3 p-3.5 cursor-pointer text-left transition-all duration-150 border-l-3 ${isSelected ? 'chat-active bg-slate-900/40' : 'border-transparent hover:bg-slate-900/30'}`}
                >
                  <div className="relative">
                    {c.chatType === 'GROUP' ? (
                      <div className="bg-violet-600/20 p-2.5 rounded-full text-violet-400 border border-violet-500/20">
                        <Users className="w-5 h-5" />
                      </div>
                    ) : (
                      <img 
                        src={c.avatarUrl || `https://api.dicebear.com/7.x/bottts/svg?seed=${c.name}`} 
                        alt="Avatar" 
                        className="w-10 h-10 rounded-full bg-slate-900 border border-indigo-500/20"
                      />
                    )}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex justify-between items-baseline">
                      <h3 className="text-sm font-bold text-white tracking-wide truncate">{c.name}</h3>
                      <span className="text-[10px] text-indigo-400 font-semibold px-1.5 py-0.5 rounded bg-indigo-500/10 uppercase tracking-wider scale-90">
                        {c.chatType}
                      </span>
                    </div>
                    <p className="text-xs text-gray-400 truncate mt-0.5">{c.description || 'No description'}</p>
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>

      {/* Chat Pane Window */}
      <div className="flex-1 flex flex-col h-full bg-slate-950/20">
        {activeChat ? (
          <>
            {/* Conversation Active Header */}
            <div className="glass-chat p-4 border-b border-slate-900 flex items-center justify-between z-10 shrink-0">
              <div className="flex items-center gap-3 text-left">
                {activeChat.chatType === 'GROUP' ? (
                  <div className="bg-violet-600/20 p-2 rounded-full text-violet-400 border border-violet-500/20">
                    <Users className="w-5 h-5" />
                  </div>
                ) : (
                  <img 
                    src={activeChat.avatarUrl || `https://api.dicebear.com/7.x/bottts/svg?seed=${activeChat.name}`} 
                    alt="Avatar" 
                    className="w-9 h-9 rounded-full bg-slate-900 border border-indigo-500/20"
                  />
                )}
                <div>
                  <h2 className="text-base font-bold text-white tracking-wide">{activeChat.name}</h2>
                  <p className="text-xs text-gray-400 truncate max-w-[200px] sm:max-w-md">{activeChat.description}</p>
                </div>
              </div>
              
              <div className="flex gap-2">
                {activeChat.chatType === 'GROUP' && (
                  <button 
                    onClick={() => setShowAddMemberModal(true)} 
                    title="Add Member"
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white font-semibold text-xs transition-colors cursor-pointer"
                  >
                    <UserPlus className="w-3.5 h-3.5" />
                    <span>Add Member</span>
                  </button>
                )}
              </div>
            </div>

            {/* Message Streams Area */}
            <div className="flex-1 overflow-y-auto p-4 space-y-4">
              {messages.length === 0 ? (
                <div className="h-full flex flex-col items-center justify-center text-gray-500 text-sm gap-2">
                  <MessageSquare className="w-10 h-10 text-gray-600 pulse-slow" />
                  <span>No messages. Say hello to start the conversation!</span>
                </div>
              ) : (
                messages.map((msg) => {
                  const isMine = msg.senderId === myId;
                  return (
                    <div 
                      key={msg.messageId} 
                      className={`flex flex-col ${isMine ? 'items-end' : 'items-start'}`}
                    >
                      <div className="max-w-[70%] text-left">
                        {/* Display sender username for group chats */}
                        {!isMine && activeChat.chatType === 'GROUP' && (
                          <span className="text-[10px] text-indigo-400 font-bold ml-2 mb-1 block uppercase tracking-wide">
                            {msg.senderUsername}
                          </span>
                        )}
                        <div 
                          className={`rounded-2xl px-4 py-2.5 text-sm shadow-md ${isMine ? 'bg-indigo-600 text-white rounded-tr-none' : 'bg-slate-900 border border-slate-800 text-gray-200 rounded-tl-none'}`}
                        >
                          <p className="break-words leading-relaxed">{msg.content}</p>
                          <span className="block text-[9px] text-right mt-1 text-gray-400 select-none">
                            {new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                          </span>
                        </div>
                      </div>
                    </div>
                  );
                })
              )}
              <div ref={messagesEndRef} />
            </div>

            {/* Input Message Footer */}
            <div className="glass-chat p-4 border-t border-slate-900 shrink-0">
              <form onSubmit={handleSendMessage} className="flex gap-2">
                <input
                  type="text"
                  value={messageText}
                  onChange={(e) => setMessageText(e.target.value)}
                  placeholder="Type a message..."
                  className="flex-1 bg-slate-900/60 border border-slate-800 rounded-xl px-4 py-3 text-sm text-white focus:outline-none focus:border-indigo-500 transition-colors"
                />
                <button
                  type="submit"
                  disabled={!messageText.trim() || !isConnected}
                  className="glow-btn px-5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl flex items-center justify-center transition-all disabled:opacity-55 cursor-pointer"
                >
                  <Send className="w-4 h-4" />
                </button>
              </form>
            </div>
          </>
        ) : (
          <div className="flex-1 flex flex-col items-center justify-center p-8 gap-4 select-none">
            <div className="bg-indigo-600/10 p-6 rounded-full text-indigo-400 border border-indigo-500/10 pulse-slow">
              <MessageSquare className="w-12 h-12" />
            </div>
            <div className="max-w-md text-center">
              <h2 className="text-xl font-bold text-white tracking-wide">ECHO Dashboard</h2>
              <p className="text-sm text-gray-400 mt-1.5 leading-relaxed">
                Connect and communicate. Select a chat from the sidebar or click the plus icon to start a new group hub.
              </p>
              
              <div className="grid grid-cols-2 gap-3 mt-8">
                <div className="glass rounded-xl p-3.5 flex flex-col items-center gap-1.5">
                  <ShieldCheck className="w-5 h-5 text-indigo-400" />
                  <span className="text-xs font-semibold text-white">E2E Cryptography</span>
                  <p className="text-[10px] text-gray-400">AES-256 protected DB</p>
                </div>
                <div className="glass rounded-xl p-3.5 flex flex-col items-center gap-1.5">
                  <Info className="w-5 h-5 text-violet-400" />
                  <span className="text-xs font-semibold text-white">Kafka Pipeline</span>
                  <p className="text-[10px] text-gray-400">Under 100ms latency</p>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Dialog Modals */}
      {/* Create Group Modal */}
      {showGroupModal && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="glass w-full max-w-md rounded-2xl p-6 shadow-2xl">
            <h3 className="text-lg font-bold text-white mb-4">Create New Group Hub</h3>
            <form onSubmit={handleCreateGroup} className="space-y-4">
              <div>
                <label className="block text-gray-400 text-xs font-semibold uppercase tracking-wider mb-2">Group Name</label>
                <input
                  type="text"
                  required
                  value={groupName}
                  onChange={(e) => setGroupName(e.target.value)}
                  placeholder="Enter group name"
                  className="w-full bg-slate-900 border border-slate-800 rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-indigo-500"
                />
              </div>
              <div>
                <label className="block text-gray-400 text-xs font-semibold uppercase tracking-wider mb-2">Description</label>
                <textarea
                  value={groupDesc}
                  onChange={(e) => setGroupDesc(e.target.value)}
                  placeholder="Enter group description..."
                  className="w-full bg-slate-900 border border-slate-800 rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-indigo-500 h-24 resize-none"
                />
              </div>
              <div className="flex gap-3 justify-end pt-2">
                <button
                  type="button"
                  onClick={() => setShowGroupModal(false)}
                  className="px-4 py-2 rounded-xl text-gray-400 hover:bg-slate-800 text-sm font-semibold cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 bg-indigo-600 hover:bg-indigo-500 rounded-xl text-white text-sm font-semibold cursor-pointer"
                >
                  Create Hub
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Add Member Modal */}
      {showAddMemberModal && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="glass w-full max-w-md rounded-2xl p-6 shadow-2xl text-left">
            <h3 className="text-lg font-bold text-white mb-4">Add Member to Group</h3>
            
            <div className="relative mb-4">
              <Search className="w-4 h-4 text-gray-500 absolute left-3 top-3" />
              <input
                type="text"
                value={memberSearchQuery}
                onChange={(e) => setMemberSearchQuery(e.target.value)}
                placeholder="Search user to add..."
                className="w-full bg-slate-900 border border-slate-800 rounded-xl pl-9 pr-4 py-2.5 text-sm text-white focus:outline-none focus:border-indigo-500"
              />
            </div>

            <div className="max-h-60 overflow-y-auto divide-y divide-slate-800/40 mb-4">
              {memberSearchResults.length === 0 ? (
                <p className="text-gray-500 text-xs text-center py-4">No matching users found.</p>
              ) : (
                memberSearchResults.map((user: any) => (
                  <div 
                    key={user.id} 
                    className="flex items-center justify-between py-2.5"
                  >
                    <div className="flex items-center gap-3">
                      <img src={user.avatarUrl} alt="Avatar" className="w-8 h-8 rounded-full bg-slate-900 border border-indigo-500/20" />
                      <div>
                        <h4 className="text-xs font-semibold text-white">{user.username}</h4>
                        <p className="text-[10px] text-gray-400">{user.email}</p>
                      </div>
                    </div>
                    <button
                      onClick={() => handleAddMember(user.id)}
                      className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-[10px] font-bold uppercase tracking-wide cursor-pointer transition-colors"
                    >
                      Add
                    </button>
                  </div>
                ))
              )}
            </div>

            <div className="flex justify-end">
              <button
                type="button"
                onClick={() => {
                  setMemberSearchQuery('');
                  setMemberSearchResults([]);
                  setShowAddMemberModal(false);
                }}
                className="px-4 py-2 rounded-xl text-gray-400 hover:bg-slate-800 text-sm font-semibold cursor-pointer"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
