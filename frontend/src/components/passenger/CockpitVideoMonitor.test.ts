import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CockpitVideoMonitor from './CockpitVideoMonitor.vue'

const apiMocks = vi.hoisted(() => ({
  getCockpitVideoConfig: vi.fn(),
}))

vi.mock('../../api/passengerRealtime', () => ({
  getCockpitVideoConfig: apiMocks.getCockpitVideoConfig,
}))

describe('CockpitVideoMonitor', () => {
  beforeEach(() => {
    apiMocks.getCockpitVideoConfig.mockReset()
  })

  it('shows a loading state while reading the configuration', () => {
    apiMocks.getCockpitVideoConfig.mockReturnValue(new Promise(() => undefined))

    const wrapper = mount(CockpitVideoMonitor)

    expect(wrapper.text()).toContain('正在读取驾驶舱视频配置')
    expect(apiMocks.getCockpitVideoConfig).toHaveBeenCalledTimes(1)
  })

  it('shows the disabled state without creating an iframe', async () => {
    apiMocks.getCockpitVideoConfig.mockResolvedValue({
      enabled: false,
      protocol: 'WEBRTC',
      playbackUrl: null,
    })

    const wrapper = mount(CockpitVideoMonitor)
    await flushPromises()

    expect(wrapper.text()).toContain('驾驶舱监控未启用')
    expect(wrapper.find('iframe').exists()).toBe(false)
  })

  it('shows configuration failures and retries the request', async () => {
    apiMocks.getCockpitVideoConfig
      .mockRejectedValueOnce(new Error('后端服务不可用'))
      .mockResolvedValueOnce({ enabled: false, protocol: 'WEBRTC', playbackUrl: null })

    const wrapper = mount(CockpitVideoMonitor)
    await flushPromises()

    expect(wrapper.text()).toContain('驾驶舱视频配置读取失败')
    expect(wrapper.text()).toContain('后端服务不可用')

    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(apiMocks.getCockpitVideoConfig).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('驾驶舱监控未启用')
  })

  it('embeds the configured MediaMTX page with safe playback parameters', async () => {
    apiMocks.getCockpitVideoConfig.mockResolvedValue({
      enabled: true,
      protocol: 'WEBRTC',
      playbackUrl: 'http://127.0.0.1:8889/cockpit?custom=value',
    })

    const wrapper = mount(CockpitVideoMonitor)
    await flushPromises()

    const iframe = wrapper.get('iframe')
    const url = new URL(iframe.attributes('src')!)
    expect(url.origin).toBe('http://127.0.0.1:8889')
    expect(url.pathname).toBe('/cockpit')
    expect(url.searchParams.get('custom')).toBe('value')
    expect(url.searchParams.get('controls')).toBe('true')
    expect(url.searchParams.get('muted')).toBe('true')
    expect(url.searchParams.get('autoplay')).toBe('true')
    expect(url.searchParams.get('playsInline')).toBe('true')
    expect(url.searchParams.get('disablepictureinpicture')).toBe('false')
    expect(iframe.attributes('allow')).toContain('autoplay')
    expect(iframe.attributes('allowfullscreen')).toBeDefined()

    await iframe.trigger('load')
    expect(wrapper.text()).not.toContain('WebRTC 播放器已加载')
  })

  it('reloads only the iframe after a valid config has been loaded', async () => {
    apiMocks.getCockpitVideoConfig.mockResolvedValue({
      enabled: true,
      protocol: 'WEBRTC',
      playbackUrl: 'http://127.0.0.1:8889/cockpit',
    })

    const wrapper = mount(CockpitVideoMonitor)
    await flushPromises()
    const originalIframe = wrapper.get('iframe').element

    await wrapper.get('button').trigger('click')

    expect(apiMocks.getCockpitVideoConfig).toHaveBeenCalledTimes(1)
    expect(wrapper.get('iframe').element).not.toBe(originalIframe)
    expect(wrapper.text()).toContain('正在加载 WebRTC 播放器')
  })
})
