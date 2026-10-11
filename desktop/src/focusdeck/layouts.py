"""Bounded, device-local grid dimensions. Achievement order remains phone-owned."""

def validate_layout(value):
    if not isinstance(value,dict) or set(value)-{'version','cards','order'} or value.get('version')!=1:raise ValueError('Invalid layout')
    cards,order=value.get('cards',{}),value.get('order',[])
    if not isinstance(cards,dict) or len(cards)>1000 or not isinstance(order,list) or len(order)>1000 or any(not isinstance(k,str) for k in order) or len(set(order))!=len(order):raise ValueError('Invalid layout cards/order')
    for k,c in cards.items():
        if not isinstance(k,str) or not 1<=len(k)<=200 or not isinstance(c,dict) or not {'span','height'}<=set(c) or set(c)-{'span','height','heightSized'}:raise ValueError('Invalid card dimensions')
        if 'heightSized' in c and type(c['heightSized']) is not bool:raise ValueError('Invalid height sizing mode')
        if type(c['span']) is not int or not 1<=c['span']<=12 or type(c['height']) is not int or not 120<=c['height']<=2400:raise ValueError('Invalid card bounds')
    if any(not isinstance(k,str) or not 1<=len(k)<=200 for k in order):raise ValueError('Invalid card order')
    return value
