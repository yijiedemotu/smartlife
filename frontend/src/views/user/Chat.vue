<template>
  <div class="page-card chat-wrap">
    <!-- 左侧：会话列表（轮询未读/最后消息） -->
    <div class="conv-list">
      <div class="conv-head">👋 搭子消息</div>
      <div v-if="!convs.length" class="empty">
        暂无会话<br />去「找饭搭子」给匹配到的人发私信吧
      </div>
      <div v-for="c in convs" :key="c.peerId" class="conv"
           :class="{ active: peerId === c.peerId }" @click="openPeer(c.peerId)">
        <el-avatar :size="42" :src="c.peer.avatar || ''">{{ nick0(c.peer) }}</el-avatar>
        <div class="conv-mid">
          <div class="conv-name">{{ c.peer.nickname }}</div>
          <div class="conv-last">{{ c.lastContent }}</div>
        </div>
        <div class="conv-right">
          <span class="conv-time">{{ shortTime(c.lastTime) }}</span>
          <el-badge v-if="Number(c.unread) > 0" :value="c.unread" class="unread-badge" />
        </div>
      </div>
    </div>

    <!-- 右侧：对话窗口 -->
    <div class="conv-main">
      <template v-if="peer">
        <div class="chat-head">
          <el-avatar :size="34" :src="peer.avatar || ''">{{ nick0(peer) }}</el-avatar>
          <span class="chat-nick">{{ peer.nickname }}</span>
          <el-tag v-if="peer.wechat" size="small" type="warning">📱 微信：{{ peer.wechat }}</el-tag>
          <el-tag v-for="t in (peer.tags || []).slice(0, 3)" :key="t" size="small" effect="plain" type="info">{{ t }}</el-tag>
        </div>
        <div ref="msgBox" class="msg-box">
          <div v-for="m in messages" :key="m.id" class="msg" :class="{ mine: m.fromId === meId }">
            <el-avatar :size="32" :src="m.fromId === meId ? (me.avatar || '') : (peer.avatar || '')">
              {{ m.fromId === meId ? nick0(me) : nick0(peer) }}
            </el-avatar>
            <div class="bubble">{{ m.content }}</div>
          </div>
          <div v-if="!messages.length" class="empty-tip">
            你们还没有聊天记录，打个招呼约个饭吧～
          </div>
        </div>
        <div class="chat-input">
          <el-input v-model="draft" placeholder="说点什么…（每 2~3 秒自动接收新消息）" maxlength="500"
                    @keyup.enter="send" />
          <el-button type="warning" :disabled="!draft.trim() || sending" :loading="sending" @click="send">发送</el-button>
        </div>
      </template>
      <div v-else class="no-peer">← 选择左侧会话开始聊天</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute } from 'vue-router'
import request from '@/utils/request'
import { useUserStore } from '@/store/user'

const route = useRoute()
const me = useUserStore()
const meId = Number(me.id)

const convs = ref([])
const peerId = ref(null)
const peer = ref(null)
const messages = ref([])
const draft = ref('')
const sending = ref(false)
const lastId = ref(0)
const msgBox = ref(null)

let contactsTimer = null
let pollTimer = null

function nick0(u) {
  return (u?.nickname || '友')[0]
}

function shortTime(t) {
  if (!t) return ''
  const s = String(t)
  return s.length >= 16 ? s.slice(5, 16) : s
}

async function scrollBottom() {
  await nextTick()
  if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
}

async function loadConversations() {
  try {
    convs.value = (await request.get('/chat/contacts', { silent: true })) || []
  } catch {
    convs.value = convs.value || []
  }
}

/** 打开某个搭子的会话：加载档案(含微信) + 初始化历史 + 轮询增量 */
async function openPeer(id) {
  if (Number(id) === meId) return
  peerId.value = Number(id)
  peer.value = await request.get(`/user/${id}`, { silent: true })
  messages.value = []
  lastId.value = 0
  const list = (await request.get(`/chat/history/${peerId.value}`, { params: { size: 50 }, silent: true })) || []
  messages.value = list
  if (list.length) lastId.value = list[list.length - 1].id
  request.post(`/chat/read/${peerId.value}`, null, { silent: true }).catch(() => {})
  scrollBottom()
  await loadConversations()
}

/** 轮询增量：取 id > lastId 的新消息 */
async function pollNew() {
  if (!peerId.value) return
  try {
    const list = (await request.get(`/chat/history/${peerId.value}`, {
      params: { afterId: lastId.value }, silent: true
    })) || []
    if (!list.length) return
    const hasNew = list.some((m) => messages.value.every((x) => x.id !== m.id))
    if (hasNew) {
      list.forEach((m) => {
        if (messages.value.every((x) => x.id !== m.id)) messages.value.push(m)
      })
      lastId.value = list[list.length - 1].id
      // 收到对方新消息立即置为已读
      if (list.some((m) => m.fromId === peerId.value)) {
        request.post(`/chat/read/${peerId.value}`, null, { silent: true }).catch(() => {})
      }
      scrollBottom()
      loadConversations()
    }
  } catch {
    /* 轮询失败静默，下轮重试 */
  }
}

async function send() {
  const content = draft.value.trim()
  if (!content || !peerId.value) return
  sending.value = true
  try {
    const msg = await request.post('/chat/send', { toId: peerId.value, content }, { silent: true })
    messages.value.push(msg)
    lastId.value = msg.id
    draft.value = ''
    scrollBottom()
    loadConversations()
  } catch (e) {
    /* 错误信息已由拦截器提示 */
  } finally {
    sending.value = false
  }
}

// 从「找饭搭子」等入口带 ?to=xxx 跳转进来时打开对应会话
watch(() => route.query.to, (v) => {
  if (v) openPeer(Number(v))
})

onMounted(async () => {
  await loadConversations()
  const to = Number(route.query.to)
  if (to && to !== meId) {
    await openPeer(to)
  }
  contactsTimer = setInterval(loadConversations, 5000)
  pollTimer = setInterval(pollNew, 2500)
})

onBeforeUnmount(() => {
  if (contactsTimer) clearInterval(contactsTimer)
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.chat-wrap { display: flex; height: 620px; padding: 0 !important; overflow: hidden; }
.conv-list { width: 280px; border-right: 1px solid #f0f0f0; overflow-y: auto; flex-shrink: 0; }
.conv-head { padding: 14px 16px; font-weight: 700; border-bottom: 1px solid #f5f5f5; position: sticky; top: 0; background: #fff; z-index: 2; }
.empty { padding: 40px 12px; text-align: center; color: #bbb; font-size: 13px; line-height: 2; }
.conv { display: flex; align-items: center; gap: 10px; padding: 12px 16px; cursor: pointer; }
.conv:hover { background: #fafafa; }
.conv.active { background: #fff7f0; }
.conv-mid { flex: 1; min-width: 0; }
.conv-name { font-weight: 600; font-size: 14px; }
.conv-last { color: #999; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conv-right { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; }
.conv-time { color: #bbb; font-size: 11px; }
.unread-badge :deep(.el-badge__content) { font-size: 10px; }
.conv-main { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.chat-head { display: flex; align-items: center; gap: 10px; padding: 12px 16px; border-bottom: 1px solid #f0f0f0; }
.chat-nick { font-weight: 700; font-size: 16px; margin-right: 4px; }
.msg-box { flex: 1; overflow-y: auto; padding: 16px; background: #fafafa; }
.msg { display: flex; gap: 8px; margin-bottom: 12px; align-items: flex-start; }
.msg.mine { flex-direction: row-reverse; }
.bubble { max-width: 60%; padding: 8px 12px; border-radius: 10px; background: #fff; line-height: 1.6; font-size: 14px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04); word-break: break-word; }
.msg.mine .bubble { background: #ff6b35; color: #fff; }
.empty-tip { text-align: center; color: #bbb; margin-top: 60px; }
.chat-input { display: flex; gap: 10px; padding: 12px; border-top: 1px solid #f0f0f0; }
.no-peer { flex: 1; display: flex; align-items: center; justify-content: center; color: #bbb; }
</style>
