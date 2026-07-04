from __future__ import annotations

import base64
import hashlib
import random
from dataclasses import dataclass

from .passengers import Passenger
from .scenario import ScenarioContext, compact_time


MOVIE_NAMES = ["星海远航", "云端恋曲", "逆袭之路", "长安奇谈", "银河竞技场", "都市风暴"]
MUSIC_NAMES = ["云上节拍", "夜航民谣", "蓝色爵士", "星光电子", "古典晨曦", "摇滚航线"]
ARTISTS = ["AirBand", "Skyline", "Blue Cabin", "Cloud Nine", "North Star"]
DOMAINS = ["www.baidu.com", "www.weixin.com", "news.example.com", "travel.example.com"]
SHOP_GOODS = [
    ("GOODS-001", "航空纪念模型", "SOUVENIR", 199.00),
    ("GOODS-002", "免税香水", "DUTYFREE", 399.00),
    ("GOODS-003", "云端套餐", "FOOD", 69.00),
    ("GOODS-004", "降噪耳机", "DIGITAL", 599.00),
]


@dataclass
class BehaviorEvent:
    passenger: Passenger
    behavior_kind: str


class IfeModel:
    def __init__(self, context: ScenarioContext, passengers: list[Passenger], rng: random.Random) -> None:
        self.context = context
        self.passengers = passengers
        self.rng = rng

    def build_633_pages(self, page_size: int) -> list[dict]:
        events = [self._event_for_633(passenger) for passenger in self.passengers]
        items = [self._to_633_item(event) for event in events]
        return self._paginate("ife_633.behavior", items, page_size)

    def build_cockrell_pages(self, page_size: int) -> list[dict]:
        events = [self._event_for_cockrell(passenger) for passenger in self.passengers]
        items = [self._to_cockrell_item(event) for event in events]
        return self._paginate("ife_cockrell.behavior", items, page_size)

    def _event_for_633(self, passenger: Passenger) -> BehaviorEvent:
        choices = ["MOVIE_PLAY", "MUSIC_PLAY", "CAST_SCREEN", "WAP_BROWSING"]
        weights = [0.45, 0.35, 0.05 if passenger.cabin_class == "FIRST" else 0.01, 0.15]
        return BehaviorEvent(passenger, self.rng.choices(choices, weights=weights, k=1)[0])

    def _event_for_cockrell(self, passenger: Passenger) -> BehaviorEvent:
        choices = ["MOVIE_PLAY", "MUSIC_PLAY", "WAP_BROWSING", "SHOPPING"]
        weights = [0.42, 0.33, 0.15, 0.10]
        return BehaviorEvent(passenger, self.rng.choices(choices, weights=weights, k=1)[0])

    def _base_item(self, passenger: Passenger) -> dict:
        return {
            "sysInfo": {
                "timestamp": compact_time(self.context.simulated_now),
                "flightId": self.context.flight_number,
            },
            "paxInfo": {
                "pnr": passenger.pnr,
                "seatNo": passenger.seat_no,
                "cabinClass": passenger.cabin_class,
                "deviceId": passenger.device_id,
                "userId": passenger.user_id,
            },
            "behaviorInfo": {},
            "extInfo": {},
        }

    def _to_633_item(self, event: BehaviorEvent) -> dict:
        item = self._base_item(event.passenger)
        item["behaviorInfo"] = self._behavior_info(event, include_cover=False)
        return item

    def _to_cockrell_item(self, event: BehaviorEvent) -> dict:
        item = self._base_item(event.passenger)
        item["behaviorInfo"] = self._behavior_info(event, include_cover=True)
        return item

    def _behavior_info(self, event: BehaviorEvent, include_cover: bool) -> dict:
        if event.behavior_kind == "MOVIE_PLAY":
            return self._movie_info(event.passenger, include_cover)
        if event.behavior_kind == "MUSIC_PLAY":
            return self._music_info(event.passenger, include_cover)
        if event.behavior_kind == "CAST_SCREEN":
            return self._cast_info()
        if event.behavior_kind == "SHOPPING":
            return self._shopping_info()
        return self._wap_info(event.passenger)

    def _movie_info(self, passenger: Passenger, include_cover: bool) -> dict:
        content_id = f"MOV-{self.rng.randint(1, 999):03d}-2026"
        info = {
            "behaviorType": "MOVIE_PLAY",
            "contentId": content_id,
            "contentName": self.rng.choice(MOVIE_NAMES),
            "contentType": self._joined_preferences(passenger.video_preferences),
            "contentDuration": self.rng.randint(90, 180),
            "playAction": self.rng.choice(["PLAY", "PAUSE", "STOP", "SEEK"]),
            "playPosition": self.rng.randint(0, 7200),
            "resolution": self.rng.choice(["720P", "1080P", "4K"]),
        }
        if include_cover:
            info.update(self._cover_fields(content_id, mime_type=self.rng.choice(["jpeg", "png"])))
        return info

    def _music_info(self, passenger: Passenger, include_cover: bool) -> dict:
        music_id = f"MUS-{self.rng.randint(1, 999):03d}-2026"
        info = {
            "behaviorType": "MUSIC_PLAY",
            "musicId": music_id,
            "musicName": self.rng.choice(MUSIC_NAMES),
            "musicType": self._joined_preferences(passenger.music_preferences),
            "artist": self.rng.choice(ARTISTS),
            "album": self.rng.choice(["云端精选", "巡航歌单", "夜航专辑", ""]),
            "playAction": self.rng.choice(["PLAY", "PAUSE", "NEXT", "PREV"]),
            "playPosition": self.rng.randint(0, 300),
            "volume": self.rng.randint(20, 90),
        }
        if include_cover:
            info.update(self._cover_fields(music_id, mime_type=self.rng.choice(["jpeg", "png"])))
        return info

    def _cast_info(self) -> dict:
        return {
            "behaviorType": "CAST_SCREEN",
            "targetDevice": self.rng.choice(["SVDU-F01", "SVDU-F02", "HEAD-CABIN-SCREEN-01"]),
            "castAction": self.rng.choice(["CAST", "STOP"]),
            "castStatus": self.rng.choice(["CONNECTED", "FAILED", "DISCONNECTED"]),
            "resolution": self.rng.choice(["720P", "1080P", "4K"]),
            "castDuration": self.rng.randint(60, 3600),
        }

    def _wap_info(self, passenger: Passenger) -> dict:
        suffix = 10 + (passenger.index % 200)
        domain = self.rng.choice(DOMAINS)
        protocol = self.rng.choice(["HTTP", "HTTPS", "TCP", "UDP"])
        port = 443 if protocol == "HTTPS" else 80 if protocol == "HTTP" else self.rng.choice([8080, 9000])
        return {
            "behaviorType": "WAP_BROWSING",
            "sessionId": f"WAP-SESSION-{passenger.index:03d}",
            "srcIp": f"192.168.1.{suffix}",
            "dstIp": f"180.101.49.{self.rng.randint(1, 254)}",
            "dstDomain": domain,
            "protocol": protocol,
            "port": port,
            "trafficBytes": self.rng.randint(10_240, 5_242_880),
            "url": f"https://{domain}/flight/{self.context.flight_number.lower()}",
        }

    def _shopping_info(self) -> dict:
        goods_id, goods_name, goods_type, unit_price = self.rng.choice(SHOP_GOODS)
        quantity = self.rng.randint(1, 3)
        shop_action = self.rng.choice(["ADD", "BUY", "PAY", "CANCEL"])
        pay_status = self.rng.choice(["UNPAID", "PAID", "FAILED", "REFUND"])
        return {
            "behaviorType": "SHOPPING",
            "orderList": [
                {
                    "orderId": f"ORDER-{self.context.simulated_now.strftime('%Y%m%d')}-{self.rng.randint(1, 999):03d}",
                    "totalPrice": round(unit_price * quantity, 2),
                    "shopAction": shop_action,
                    "payStatus": pay_status,
                    "payType": self.rng.choice(["ALIPAY", "WECHAT", "CREDITCARD"]),
                    "goodsList": [
                        {
                            "goodsId": goods_id,
                            "goodsName": goods_name,
                            "goodsType": goods_type,
                            "quantity": quantity,
                            "unitPrice": unit_price,
                            "coverBase64": base64.b64encode(goods_name.encode("utf-8")).decode("ascii"),
                            "coverMimeType": "image/png",
                        }
                    ],
                }
            ],
        }

    def _joined_preferences(self, preferences: list[str]) -> str:
        chosen_count = self.rng.randint(1, len(preferences))
        return "/".join(self.rng.sample(preferences, chosen_count))

    def _cover_fields(self, seed: str, mime_type: str) -> dict:
        raw = f"{seed}-{self.context.flight_number}".encode("utf-8")
        encoded = base64.b64encode(raw).decode("ascii")
        return {
            "coverBase64": encoded,
            "coverMimeType": mime_type,
            "coverChecksum": hashlib.sha256(encoded.encode("ascii")).hexdigest(),
        }

    def _paginate(self, message_type: str, items: list[dict], page_size: int) -> list[dict]:
        pages = []
        total = len(items)
        page_size = max(1, page_size)
        for index in range(0, total, page_size):
            pages.append(
                {
                    "messageType": message_type,
                    "sentAt": self.context.simulated_now.isoformat(),
                    "page": index // page_size + 1,
                    "pageSize": page_size,
                    "total": total,
                    "items": items[index : index + page_size],
                }
            )
        return pages

