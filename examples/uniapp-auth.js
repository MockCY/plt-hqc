const API_BASE_URL = 'https://api.example.com'

function request(options) {
  const token = uni.getStorageSync('access_token')
  return uni.request({
    ...options,
    url: `${API_BASE_URL}${options.url}`,
    header: {
      ...(options.header || {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    }
  })
}

function wechatLogin() {
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: resolve,
      fail: reject
    })
  })
}

export async function login() {
  const loginResult = await wechatLogin()
  const response = await request({
    url: '/api/auth/login',
    method: 'POST',
    data: { code: loginResult.code }
  })
  uni.setStorageSync('access_token', response.data.token)
  return response.data
}

// 在按钮的 @getphonenumber="bindWechatPhone" 事件中调用。
export async function bindWechatPhone(event) {
  const phoneCode = event.detail.code
  if (!phoneCode) {
    throw new Error('用户未授权手机号或微信没有返回 phoneCode')
  }

  // loginCode 和 phoneCode 是不同的一次性凭证，不能互相替代。
  const loginResult = await wechatLogin()
  const response = await request({
    url: '/api/auth/wechat-phone',
    method: 'POST',
    data: {
      loginCode: loginResult.code,
      phoneCode
    }
  })
  uni.setStorageSync('access_token', response.data.token)
  return response.data
}

export async function getCourses() {
  const response = await request({ url: '/api/courses', method: 'GET' })
  return response.data
}

export async function logout() {
  try {
    await request({ url: '/api/auth/logout', method: 'POST' })
  } finally {
    uni.removeStorageSync('access_token')
  }
}

