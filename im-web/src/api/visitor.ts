import axios from 'axios';
import JSONbig from 'json-bigint';
import { API_BASE_URL, TENANT_ID } from '../config';
import type { AgentAvailabilityResponse, ApiEnvelope, WidgetConfigResponse, WidgetSessionResponse } from './types';

const json = JSONbig({ storeAsString: true });

const visitorClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  transformResponse: [parseJsonSafely],
  headers: {
    'X-Tenant-Id': String(TENANT_ID),
  },
});

export async function enterVisitorWidget(visitorToken: string): Promise<WidgetSessionResponse> {
  const response = await visitorClient.post<ApiEnvelope<WidgetSessionResponse>>('/api/v1/cs/widget/sessions', {
    visitorToken,
  });
  return unwrapEnvelope(response.data);
}

export async function getVisitorWidgetConfig(): Promise<WidgetConfigResponse> {
  const response = await visitorClient.get<ApiEnvelope<WidgetConfigResponse>>('/api/v1/cs/widget/config');
  return unwrapEnvelope(response.data);
}

export async function getVisitorAgentAvailability(): Promise<AgentAvailabilityResponse> {
  const response = await visitorClient.get<ApiEnvelope<AgentAvailabilityResponse>>('/api/v1/cs/widget/availability');
  return unwrapEnvelope(response.data);
}

function unwrapEnvelope<T>(payload: ApiEnvelope<T> | T): T {
  if (payload && typeof payload === 'object' && 'code' in payload && 'message' in payload) {
    const envelope = payload as ApiEnvelope<T>;
    if (envelope.code !== 0) {
      throw new Error(envelope.message || `接口失败：${envelope.code}`);
    }
    return envelope.data;
  }
  return payload as T;
}

function parseJsonSafely(data: unknown) {
  if (typeof data !== 'string' || data.length === 0) {
    return data;
  }
  try {
    return json.parse(data);
  } catch {
    return data;
  }
}
