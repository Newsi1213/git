/* CampusQuest 公共 Vue 组件（x-template 写法，Vue2，无脚手架） */
(function (window) {
  'use strict';

  var templates = {
    'topbar': '<div class="topbar">'
      + '<div class="brand"><div class="logo-mark">CQ</div><div class="brand-name">CampusQuest</div>'
      + '<div class="brand-sub">校园互助悬赏平台</div></div>'
      + '<div class="top-right">'
      + '<span class="balance">积分 <b>{{ me.points || 0 }}</b> <i>分</i></span>'
      + '<div class="bell" title="消息中心" @click="toMessages">信'
      + '<span class="dot" v-if="me.unreadCount > 0">{{ me.unreadCount }}</span></div>'
      + '<div class="avatar-ph" :title="me.nickname" @click="toProfile">{{ initial }}</div>'
      + '</div></div>',
    'sidebar': '<aside class="side"><div class="side-title">学生端</div><nav class="nav">'
      + '<a v-for="m in menus" :key="m.key" :href="m.href" :class="{ active: active === m.key }">'
      + '<span class="nav-ico">{{ m.ico }}</span>{{ m.name }}</a>'
      + '</nav></aside>',
    'task-card': '<div class="task-card" @click="open">'
      + '<span class="task-badge">{{ task.applicationCount || 0 }} 人申请</span>'
      + '<div class="task-cat"><span class="cat-dot" :style="{ background: catColor }"></span>{{ catName }}</div>'
      + '<div class="task-title">{{ task.title }}</div>'
      + '<div class="task-bottom"><div class="task-points">{{ task.points }}<span>积分</span></div>'
      + '<status-tag :status="task.status"></status-tag></div>'
      + '<div class="task-user"><span>{{ task.publisherNickname }}</span>'
      + '<span class="task-credit">信用 {{ task.publisherCredit }}</span></div>'
      + '<div class="task-time">{{ task.createdAt }}</div>'
      + '</div>',
    'line-chart': '<div class="line-chart-wrap"><svg viewBox="0 0 620 210" width="100%" height="230">'
      + '<line x1="40" y1="16" x2="40" y2="176" stroke="#e4e7ed"></line>'
      + '<line x1="40" y1="176" x2="604" y2="176" stroke="#e4e7ed"></line>'
      + '<text x="8" y="20" font-size="11" fill="#909399">{{ maxValue }}</text>'
      + '<text x="30" y="180" font-size="11" fill="#909399">0</text>'
      + '<polyline :points="polyline" fill="none" stroke="#2f3a4a" stroke-width="2"></polyline>'
      + '<g v-for="p in points" :key="p.date">'
      + '<circle :cx="p.x" :cy="p.y" r="3.5" fill="#fff" stroke="#2f3a4a" stroke-width="2"></circle>'
      + '<text :x="p.x" :y="p.y - 9" text-anchor="middle" font-size="11" fill="#5a5e66">{{ p.count }}</text>'
      + '<text :x="p.x" y="194" text-anchor="middle" font-size="11" fill="#909399">{{ p.label }}</text>'
      + '</g></svg></div>',
    'review-dialog': '<el-dialog title="评价 TA" :visible="visible" width="440px" @close="$emit(\'close\')">'
      + '<div class="review-form">'
      + '<p class="muted small">任务：{{ taskTitle }}</p>'
      + '<el-radio-group v-model="rating">'
      + '<el-radio label="GOOD">好评（对方信用 +3）</el-radio>'
      + '<el-radio label="BAD">差评（对方信用 -3）</el-radio>'
      + '</el-radio-group>'
      + '<el-input type="textarea" :rows="3" maxlength="100" show-word-limit v-model="comment"'
      + ' placeholder="补充说明（选填，最多 100 字）"></el-input>'
      + '<div class="dialog-actions">'
      + '<el-button size="small" @click="$emit(\'close\')">取消</el-button>'
      + '<el-button size="small" type="primary" :disabled="!rating" @click="submit">提交评价</el-button>'
      + '</div></div></el-dialog>',
    'chat-panel': '<div class="chat-panel">'
      + '<div class="chat-panel-head">'
      + '<div class="who"><span class="mini-avatar">聊</span>{{ title }}</div>'
      + '<span class="muted small">每 5 秒自动刷新</span>'
      + '</div>'
      + '<div class="chat-box" ref="box" v-loading="loading">'
      + '<div v-for="m in messages" :key="m.id" class="chat-row" :class="{ mine: m.mine }">'
      + '<div class="chat-bubble">'
      + '<img v-if="m.msgType === \'IMAGE\'" :src="m.imagePath" :alt="\'聊天图片\'" @click="openImage(m)">'
      + '<span v-else>{{ m.content }}</span>'
      + '</div></div>'
      + '<div class="chat-time" v-if="messages.length > 0">共 {{ messages.length }} 条消息</div>'
      + '</div>'
      + '<div class="chat-input">'
      + '<input type="text" v-model="text" maxlength="500" placeholder="输入消息…" @keyup.enter="sendText">'
      + '<el-upload :auto-upload="false" :show-file-list="false" accept="image/*" :on-change="onPick">'
      + '<div class="img-btn">发图</div></el-upload>'
      + '<button class="send-btn" type="button" :disabled="sending" @click="sendText">发送</button>'
      + '</div>'
      + '<div class="muted small mt8">支持 jpg / jpeg / png / gif / webp，单张不超过 5MB，图片点击可看原图。</div>'
      + '</div>'
  };

  Object.keys(templates).forEach(function (key) {
    var el = document.createElement('script');
    el.type = 'text/x-template';
    el.id = 'tpl-' + key;
    el.innerHTML = templates[key];
    document.body.appendChild(el);
  });

  Vue.component('cq-topbar', {
    props: { me: { type: Object, default: function () { return {}; } } },
    template: '#tpl-topbar',
    computed: {
      initial: function () {
        return (this.me.nickname || '同').substring(0, 1);
      }
    },
    methods: {
      toMessages: function () { location.href = 'messages.html'; },
      toProfile: function () { location.href = 'profile.html'; }
    }
  });

  Vue.component('cq-sidebar', {
    props: { active: { type: String, default: '' } },
    template: '#tpl-sidebar',
    data: function () {
      return { menus: CQ.menus };
    }
  });

  Vue.component('cq-empty', {
    props: { text: { type: String, default: '暂无数据' } },
    template: '<div class="empty-state"><div class="es-ico">·</div><p>{{ text }}</p></div>'
  });

  Vue.component('status-tag', {
    props: { status: { type: String, default: '' }, offline: { type: Number, default: 0 } },
    template: '<span :class="[\'status-tag\', cls]">{{ text }}</span>',
    computed: {
      cls: function () {
        return this.offline === 1 ? 'st-OFFLINE' : ('st-' + this.status);
      },
      text: function () {
        if (this.offline === 1) return '已下架';
        return CQ.dict.taskStatus[this.status] || this.status;
      }
    }
  });

  Vue.component('task-card', {
    props: { task: { type: Object, required: true } },
    template: '#tpl-task-card',
    computed: {
      catName: function () { return CQ.dict.categoryName(this.task.category); },
      catColor: function () { return CQ.dict.categoryColor(this.task.category); }
    },
    methods: {
      open: function () {
        location.href = 'task-detail.html?id=' + this.task.id;
      }
    }
  });

  Vue.component('review-dialog', {
    props: {
      visible: { type: Boolean, default: false },
      taskTitle: { type: String, default: '' }
    },
    template: '#tpl-review-dialog',
    data: function () {
      return { rating: '', comment: '' };
    },
    watch: {
      visible: function (v) {
        if (v) {
          this.rating = '';
          this.comment = '';
        }
      }
    },
    methods: {
      submit: function () {
        this.$emit('submit', { rating: this.rating, comment: this.comment });
      }
    }
  });

  Vue.component('chat-panel', {
    props: {
      conversationId: { type: Number, default: 0 },
      title: { type: String, default: '任务聊天' }
    },
    template: '#tpl-chat-panel',
    data: function () {
      return { messages: [], text: '', lastId: 0, loading: false, sending: false, timer: null };
    },
    watch: {
      conversationId: function (v) { this.reset(v); }
    },
    mounted: function () {
      this.reset(this.conversationId);
    },
    beforeDestroy: function () {
      if (this.timer) clearInterval(this.timer);
    },
    methods: {
      reset: function (id) {
        if (this.timer) { clearInterval(this.timer); this.timer = null; }
        this.messages = [];
        this.lastId = 0;
        this.text = '';
        if (!id) return;
        this.load(true);
        this.timer = setInterval(this.poll, 5000);
      },
      load: function (initial) {
        var self = this;
        if (!this.conversationId) return;
        if (initial) this.loading = true;
        var params = initial ? { page: 1, size: 100 } : { afterId: this.lastId, size: 50 };
        CQ.api.get('/conversations/' + this.conversationId + '/messages', { params: params }).then(function (data) {
          var list = (data && data.list) || [];
          if (initial) {
            self.messages = list;
          } else if (list.length > 0) {
            self.messages = self.messages.concat(list);
          }
          for (var i = 0; i < list.length; i++) {
            if (list[i].id > self.lastId) self.lastId = list[i].id;
          }
          self.loading = false;
          if (initial || list.length > 0) self.scrollBottom();
        }).catch(function () {
          self.loading = false;
        });
      },
      poll: function () {
        this.load(false);
      },
      append: function (msg) {
        if (!msg || msg.id <= this.lastId) return;
        this.messages.push(msg);
        this.lastId = msg.id;
        this.scrollBottom();
      },
      scrollBottom: function () {
        var self = this;
        this.$nextTick(function () {
          var box = self.$refs.box;
          if (box) box.scrollTop = box.scrollHeight;
        });
      },
      sendText: function () {
        var self = this;
        var content = (this.text || '').trim();
        if (!content || this.sending) return;
        this.sending = true;
        CQ.api.post('/conversations/' + this.conversationId + '/messages', { content: content }).then(function (msg) {
          self.text = '';
          self.append(msg);
        }).catch(function () {}).finally(function () {
          self.sending = false;
        });
      },
      onPick: function (file) {
        var self = this;
        if (!file || !file.raw || this.sending) return;
        var form = new FormData();
        form.append('file', file.raw);
        this.sending = true;
        CQ.api.post('/conversations/' + this.conversationId + '/images', form).then(function (msg) {
          ElementUI.Message.success('图片已发送');
          self.append(msg);
        }).catch(function () {}).finally(function () {
          self.sending = false;
        });
      },
      openImage: function (m) {
        window.open(m.imagePath, '_blank');
      }
    }
  });

  Vue.component('line-chart', {
    props: { data: { type: Array, default: function () { return []; } } },
    template: '#tpl-line-chart',
    computed: {
      maxValue: function () {
        var max = 0;
        this.data.forEach(function (d) { if (d.count > max) max = d.count; });
        if (max === 0) return 5;
        return Math.ceil(max / 5) * 5;
      },
      points: function () {
        var self = this;
        var w = 620, h = 200, left = 40, right = 16, top = 16, bottom = 34;
        var innerW = w - left - right;
        var innerH = h - top - bottom;
        var step = this.data.length > 1 ? innerW / (this.data.length - 1) : 0;
        return this.data.map(function (d, i) {
          var x = left + step * i;
          var y = top + innerH - (d.count / self.maxValue) * innerH;
          return { x: x, y: y, count: d.count, date: d.date, label: String(d.date).substring(5) };
        });
      },
      polyline: function () {
        return this.points.map(function (p) { return p.x + ',' + p.y; }).join(' ');
      }
    }
  });
})(window);
