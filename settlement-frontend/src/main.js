import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import SettlementForm from './views/SettlementForm.vue'
import SettlementList from './views/SettlementList.vue'
import HoldingsView from './views/HoldingsView.vue'

const routes = [
  { path: '/', redirect: '/settlement/new' },
  { path: '/settlement/new', component: SettlementForm, name: 'NewSettlement' },
  { path: '/settlement/list', component: SettlementList, name: 'SettlementList' },
  { path: '/holdings', component: HoldingsView, name: 'Holdings' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const app = createApp(App)
app.use(router)
app.mount('#app')
