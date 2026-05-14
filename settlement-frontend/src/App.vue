<template>
  <div class="app">
    <nav class="navbar">
      <div class="navbar-brand">
        <h1>Bond Settlement System</h1>
      </div>
      <div class="navbar-links">
        <div class="nav-group">
          <span class="nav-group-label">Pre-Settlement</span>
          <router-link to="/matching" class="nav-link">Trade Matching</router-link>
        </div>
        <div class="nav-divider"></div>
        <div class="nav-group">
          <span class="nav-group-label">Settlement</span>
          <router-link to="/settlement/new" class="nav-link">SWIFT Direct</router-link>
          <router-link to="/settlement/list" class="nav-link">Monitor</router-link>
          <router-link to="/dvp" class="nav-link">DVP</router-link>
          <router-link to="/partial-settlement" class="nav-link">Partial</router-link>
        </div>
        <div class="nav-divider"></div>
        <div class="nav-group">
          <span class="nav-group-label">Reference</span>
          <router-link to="/holdings" class="nav-link">Holdings</router-link>
          <router-link to="/counterparty" class="nav-link">Counterparties</router-link>
        </div>
      </div>
    </nav>
    <main class="content">
      <router-view />
    </main>
  </div>
</template>

<script>
export default {
  name: 'App'
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background-color: #f5f7fa;
  color: #2c3e50;
}

.app {
  min-height: 100vh;
}

.navbar {
  background: linear-gradient(135deg, #1a237e, #283593);
  padding: 0 2rem;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.navbar-brand h1 {
  color: #fff;
  font-size: 1.25rem;
  font-weight: 600;
}

.navbar-links {
  display: flex;
  gap: 0.25rem;
  align-items: center;
}

.nav-group {
  display: flex;
  align-items: center;
  gap: 0.15rem;
}

.nav-group-label {
  color: rgba(255, 255, 255, 0.45);
  font-size: 0.7rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 0 0.35rem;
  font-weight: 600;
}

.nav-divider {
  width: 1px;
  height: 24px;
  background: rgba(255, 255, 255, 0.2);
  margin: 0 0.35rem;
}

.nav-link {
  color: rgba(255, 255, 255, 0.85);
  text-decoration: none;
  padding: 0.5rem 1rem;
  border-radius: 6px;
  font-size: 0.9rem;
  transition: all 0.2s;
}

.nav-link:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
}

.nav-link.router-link-active {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
  font-weight: 500;
}

.content {
  max-width: 1200px;
  margin: 2rem auto;
  padding: 0 1.5rem;
}
</style>
