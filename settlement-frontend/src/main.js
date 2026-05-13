import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import SettlementForm from './views/SettlementForm.vue'
import SettlementList from './views/SettlementList.vue'
import HoldingsView from './views/HoldingsView.vue'
import MessagesView from './views/MessagesView.vue'
import CounterpartyView from './views/CounterpartyView.vue'

const routes = [
  { path: '/', redirect: '/settlement/new' },
  { path: '/settlement/new', component: SettlementForm, name: 'NewSettlement' },
  { path: '/settlement/list', component: SettlementList, name: 'SettlementList' },
  { path: '/settlement/:tradeRef/messages', component: MessagesView, name: 'Messages' },
  { path: '/holdings', component: HoldingsView, name: 'Holdings' },
  { path: '/counterparty', component: CounterpartyView, name: 'Counterparty' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const app = createApp(App)
app.use(router)
app.mount('#app')
