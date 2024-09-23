
{
    "jinni_set_widget": {
        "variables": {
            "jinni_id": "00b3b3ab-7ebf-58fa-b365-590c3319eea3",
            "verification" : {
                "signature":  "0x8c02f41ab4c9428dca08a7533b33d765534ecdab24bfba71741039765d5103b3473f0b246922ebb007196e7c336f88528d1249f6504c3a0ab6fe8db6fca9dd011b",
                "_raw_query": " mutation jinni_set_widget( $verification: SignedRequest!, $jinni_id: String!, $widgets: [WidgetSettingInput]! ) { jinni_set_widget( verification: $verification, jinni_id: $jinni_id, widgets: $widgets ) } "
            },
            "widgets": [
                {"id": "stat-djinn", "provider": "MaliksMajik", "priority": 5},
                {"id": "stat-health", "provider": "MaliksMajik", "priority": 5},
                {"id": "stat-strength", "provider": "MaliksMajik", "priority": 5},
                {"id": "stat-intelligence", "provider": "MaliksMajik", "priority": 5},
                {"id": "stat-community", "provider": "MaliksMajik", "priority": 5},
                {"id": "maliksmajik-avatar-viewer",
                "intentions": ["creative expression", "community living", "unleashing inner child", "sexy physique", "being vulnerable"],
                "mood": ["jubilant", "pensive", "loving", "dominant", "sassy"],
                "priority": 5,
                "provider": "MaliksMajik",
                "stats": ["Djinn", "Health", "Strength", "Intelligence", "Community"]}
            ]
        }
    }
}